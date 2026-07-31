# Code Review — Day 2 Messaging (ActiveMQ + RabbitMQ)

**Score: 7 / 10**

---

## Scope

| | |
|---|---|
| New files | 15 |
| Modified files | 5 |
| Approx LOC delta | ~400 |
| Focus | Full review of all changed files |

---

## Overall Assessment

Clean, well-structured implementation that follows the project's existing patterns (service/impl split, `@RequiredArgsConstructor`, `@Transactional` per method, `BaseEntity` inheritance). The two-broker topology is correctly wired: ActiveMQ handles per-user booking events via point-to-point queue; RabbitMQ fan-out delivers tour promotions to two independent consumers. No injection, auth-bypass, or data-leak issues found.

Three issues need attention before this is production-safe: lazy-load access on a plain `findById` result inside `sendBookingNotification`; missing broker config in the prod profile; and `setTrustAllPackages(true)` not gated to the dev profile.

---

## Critical Issues

### C1 — LazyInitializationException risk in `sendBookingNotification`

**File:** `BookingServiceImpl.java:197–254`

`adminConfirmBooking` and `adminCancelBooking` both call `bookingRepository.findById(bookingId)` — this is a plain JPA `findById` that returns a Booking with `user` and `tour` as uninitialized LAZY proxies. `sendBookingNotification` then accesses both `booking.getTour().getTitle()` (line 246) and `booking.getUser().getId()` (line 249) within the same `@Transactional` method, so Hibernate *can* still resolve the proxies — but only as long as the session is open.

The existing pattern in this file for any code that accesses related entities is `findByIdWithTourAndUser` (used in `getBookingDetail` and `getBookingById`). The new admin methods deviate from that pattern, making them fragile: if either association is ever changed to a detached load path or the code is refactored, a `LazyInitializationException` will surface in production (where `open-in-view=false` is set).

`cancelBooking` (line 175–188) has the same pattern: `findById` + `booking.getUser().getId()` within `@Transactional`. That pre-existed this diff, so it is noted for consistency but is not new.

**Fix:** Replace both `findById` calls in `adminConfirmBooking` and `adminCancelBooking` with `findByIdWithTourAndUser`:

```java
Booking booking = bookingRepository.findByIdWithTourAndUser(bookingId)
        .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
```

---

### C2 — Prod profile has no broker configuration — application will not start

**File:** `application-prod.properties`

`application-dev.properties` defines `spring.activemq.broker-url` and `spring.rabbitmq.*`. The prod profile defines none of these. At startup Spring Boot's auto-configuration will fail to create a `ConnectionFactory` for RabbitMQ and will throw `UnsatisfiedDependencyException` before any request is served.

**Fix:** Add environment-variable-backed broker config to `application-prod.properties`:

```properties
# ActiveMQ
spring.activemq.broker-url=${ACTIVEMQ_BROKER_URL}

# RabbitMQ
spring.rabbitmq.host=${RABBITMQ_HOST}
spring.rabbitmq.port=${RABBITMQ_PORT:5672}
spring.rabbitmq.username=${RABBITMQ_USERNAME}
spring.rabbitmq.password=${RABBITMQ_PASSWORD}
spring.rabbitmq.virtual-host=${RABBITMQ_VHOST:/}
```

---

### C3 — `setTrustAllPackages(true)` not profile-gated

**File:** `ActiveMQConfig.java:25`

`setTrustAllPackages(true)` disables ActiveMQ's class-whitelist deserialization guard globally, for all profiles. The comment says "Dev-only" but there is no `@Profile("dev")` or `@ConditionalOnProperty` guard. If the same config bean is loaded in prod it exposes an object-deserialization vector (remote code execution via crafted JMS messages — equivalent to CVE-2015-5254 class of vulnerabilities).

**Fix (option A):** Split the bean and guard it:

```java
@Bean
@Profile("dev")
public ActiveMQConnectionFactory activeMQConnectionFactory() {
    ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
    factory.setTrustAllPackages(true);   // embedded broker only
    return factory;
}

@Bean
@Profile("!dev")
public ActiveMQConnectionFactory activeMQConnectionFactorySecure() {
    ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
    factory.setTrustedPackages(List.of("com.sunasterisk.bookingtours.messaging.activemq",
                                       "com.sunasterisk.bookingtours.entity"));
    return factory;
}
```

**Fix (option B — simpler):** Always use `setTrustedPackages` with the exact two packages needed; drop `setTrustAllPackages`.

---

## High Priority

### H1 — `update()` fires promotion when status is null (no-change case)

**File:** `TourServiceImpl.java:151`

```java
publishIfActive(saved.getId(), saved.getTitle(), tourRequest.getStatus());
```

When an admin updates a tour's fields without touching the status, `tourRequest.getStatus()` is `null`. `publishIfActive` checks `status == TourStatus.ACTIVE` so null is safe here — **no publish fires**. However, if the persisted tour is already `ACTIVE` and the admin edits an unrelated field (e.g. price), **no promotion is published**, which is correct behaviour. But the contrast with `create()` — which passes `saved.getStatus()` — is confusing and inconsistent. Should `update()` fire a promotion when the tour remains ACTIVE and gets updated? The current code never does. This is arguably correct (avoid duplicate promotions), but worth a deliberate comment.

More concretely: a status change from `DRAFT → ACTIVE` via an update fires the promotion (because `tourRequest.getStatus()` is `ACTIVE`), which is correct. A status change from `ACTIVE → DRAFT` does not suppress a queued-but-not-yet-consumed promotion — minor gap, low risk.

**Recommended action:** Add a one-line comment to `publishIfActive` call in `update()` explaining the null-means-no-change intent.

---

### H2 — `broadcastTourPromotion` does unbounded bulk insert

**File:** `NotificationServiceImpl.java:58–78`

`findAllActiveUserIds()` returns all active user IDs in a single query with no pagination. `saveAll()` inserts one row per user in a single transaction. With thousands of active users this will:
- Hold a long-lived write transaction
- Generate a batch of INSERTs that blocks the notifications table for the duration
- Potentially OOM on the ID list itself

For this educational project this is acceptable, but for a realistic prod workload it should be chunked (`Lists.partition`) or moved to an async batch job.

**Action:** Note in code comment or backlog; no immediate block for merge.

---

### H3 — RabbitMQ test context will attempt a real TCP connection

**File:** `application-test.properties:41–44`

The test properties point `spring.rabbitmq.host=localhost` with no `spring.autoconfigure.exclude` for `RabbitAutoConfiguration`. On a CI machine with no RabbitMQ daemon the `contextLoads` test will fail with a connection timeout or `AmqpConnectException`.

ActiveMQ is fine (embedded vm:// broker). RabbitMQ is not embedded and has no equivalent.

**Fix:** Either add an `@MockBean RabbitTemplate` in the test class or exclude the auto-configuration:

```properties
# application-test.properties
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration
```

---

## Minor Issues

### M1 — `Notification` entity stores `userId` as a scalar, not a relation

**File:** `Notification.java:23`

`userId` is mapped as `@Column(name = "user_id")` — a raw `Long` — rather than `@ManyToOne`. The FK constraint exists at the DB level (V7 migration). This is a deliberate pattern to avoid loading the User graph when working with notifications. It is consistent with the project and acceptable, but it means JPA cannot navigate `notification.user` directly. Document this intent with a comment if it is intentional.

### M2 — `NotificationDto` exposes no `userId`

**File:** `NotificationDto.java`

The DTO omits `userId`. This is correct for per-user API endpoints (user already knows their own ID). If a future admin endpoint lists notifications across users, the DTO will need extending. Fine for now.

### M3 — `BookingNotificationMessage.serialVersionUID = 1L`

**File:** `BookingNotificationMessage.java:15`

`1L` is a placeholder. Any structural change to the class (adding/removing fields) without bumping `serialVersionUID` can cause silent deserialization errors if old messages are still in the queue when the new version starts. Use IDE-generated UID or bump to a meaningful value when the class changes. Low risk with an embedded broker that drains on restart.

### M4 — `TourPromotionMessage` lacks `Serializable` / has no `@JsonProperty`

**File:** `TourPromotionMessage.java`

Sent via RabbitMQ with `Jackson2JsonMessageConverter` — serialization is JSON, not Java, so `Serializable` is not required. Jackson will use Lombok `@Getter` for serialization and `@NoArgsConstructor` + `@Setter` for deserialization. This works correctly. No action needed, just noting the contrast with the ActiveMQ message.

### M5 — `markAllReadByUserId` `@Modifying` without `clearAutomatically`

**File:** `NotificationRepository.java:17`

`@Modifying` bulk-update queries bypass the Hibernate first-level cache. If another operation in the same transaction reads `Notification` entities after `markAllReadByUserId`, stale entities with `isRead=false` can be returned. For this service this is unlikely (the method is called standalone), but it is best practice to add `clearAutomatically = true`:

```java
@Modifying(clearAutomatically = true)
```

---

## Observations

- **Architecture fit:** The messaging layer follows the established service/impl pattern cleanly. `BookingNotificationProducer` and `TourPromotionPublisher` are thin wrappers — correct KISS application.
- **Fanout topology:** Using a fanout exchange with two dedicated queues for notification and logging is the right pattern; each consumer is independently scalable.
- **SQL migration V7:** Migration is clean, has correct composite index `idx_notifications_user_unread (user_id, is_read)` which covers the most important query (`countByUserIdAndIsReadFalse`). `ON DELETE CASCADE` is correct.
- **No error swallowing:** Both `BookingNotificationConsumer.onMessage` and `TourPromotionNotificationListener.onMessage` let exceptions propagate. ActiveMQ will retry then DLQ; RabbitMQ will requeue. This is the right default for these workloads.
- **No test coverage for messaging:** No unit/integration tests exist for the new producers or consumers. The tester report should flag this.
- **`guest`/`guest` credentials in dev properties:** Acceptable for local Docker development; not a security concern since they never reach prod.
- **`vm://localhost` in test properties lacks `broker.persistent=false`:** The dev URL has it; the test URL does not. The embedded broker will create `.activemq` data directories in the working directory during tests. Not a correctness issue; just leaves artefacts on disk.

---

## Recommended Actions (priority order)

1. **[C1]** Switch `adminConfirmBooking` and `adminCancelBooking` to `findByIdWithTourAndUser` to guarantee lazy association safety.
2. **[C2]** Add env-var broker config to `application-prod.properties` before any prod deployment.
3. **[C3]** Gate `setTrustAllPackages(true)` to `@Profile("dev")` only, or switch to `setTrustedPackages`.
4. **[H3]** Exclude or mock `RabbitAutoConfiguration` in test context to prevent CI failures on machines without a RabbitMQ daemon.
5. **[H1]** Add clarifying comment to `publishIfActive` call in `update()`.
6. **[M5]** Add `clearAutomatically = true` to `@Modifying` on `markAllReadByUserId`.
7. **[M3]** Plan to bump `serialVersionUID` whenever `BookingNotificationMessage` is changed.

---

## Metrics

| Metric | Value |
|---|---|
| Type Coverage | High — all public APIs typed, no raw `Object` params |
| Test Coverage (messaging) | 0% — no tests for producers, consumers, or `NotificationServiceImpl` |
| Linting Issues | None found |
| Critical Issues | 3 |
| High Issues | 3 |
| Minor Issues | 5 |

---

## Unresolved Questions

1. Does the prod deployment plan include an ActiveMQ broker or should ActiveMQ be replaced with embedded-only? If ActiveMQ remains embedded in prod, C2 for the ActiveMQ piece can be dropped.
2. Is the `broadcastTourPromotion` expected to scale to thousands of users? If yes, H2 needs chunking before prod launch.
