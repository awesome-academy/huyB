# Phase 01 — ActiveMQ Dependency & Configuration (T2.1)

## Context links
- Spec: `../spec/t2-messaging.md`
- Plan: `plan.md`

## Overview
- **Priority:** P2
- **Status:** completed
- **Depends on:** none (parallel with 02, 05)
- Wire embedded ActiveMQ broker (`vm://localhost`), JmsTemplate, and the `booking.notifications` queue bean. No producer/consumer yet — just infra so the app boots with JMS enabled.

## Key insights
- Spring Boot 4.0.6 / Java 21. Use `spring-boot-starter-activemq` (managed version from parent) + `activemq-broker` for embedded mode.
- Embedded broker via `vm://localhost?broker.persistent=false` — no external process, ideal for dev/test.
- `@EnableJms` activates `@JmsListener` scanning (needed by phase 03, but declared here on the config).
- Existing config classes live in `config/` and use plain `@Configuration @Bean` style (see `SwaggerConfig`, `WebMvcConfig`).

## Requirements
**Functional**
- App boots; log shows embedded ActiveMQ broker started.
- Bean `Queue booking.notifications` available for injection.

**Non-functional**
- Non-persistent broker (in-memory) — zero disk state.
- Config isolated to `dev` and default profiles via properties file.

## Architecture / data flow
No runtime data yet. This phase only registers beans:
`ActiveMQConnectionFactory` → `JmsTemplate` → used later by producer; `Queue` bean → used by producer & consumer destination name.

## Related code files
**Modify**
- `pom.xml` — add 2 dependencies
- `src/main/resources/application-dev.properties` — add ActiveMQ block

**Create**
- `src/main/java/com/sunasterisk/bookingtours/config/ActiveMQConfig.java`

## Implementation steps
1. In `pom.xml`, add inside `<dependencies>`:
   - `org.springframework.boot:spring-boot-starter-activemq`
   - `org.apache.activemq:activemq-broker` (embedded broker; version managed by starter/parent — verify, add explicit version only if unmanaged).
2. Create `config/ActiveMQConfig.java`:
   - Class annotated `@Configuration` + `@EnableJms`.
   - `@Bean ActiveMQConnectionFactory activeMQConnectionFactory(@Value("${spring.activemq.broker-url}") String url)` → returns `new ActiveMQConnectionFactory(url)`; call `setTrustAllPackages(true)` (or set trusted packages to the messaging DTO package) so JMS object serialization of `BookingNotificationMessage` works.
   - `@Bean JmsTemplate jmsTemplate(ConnectionFactory cf)` → `new JmsTemplate(cf)`.
   - `@Bean Queue bookingNotificationsQueue()` → `new ActiveMQQueue("booking.notifications")`. Prefer a shared constant for the queue name (e.g. `public static final String BOOKING_NOTIFICATIONS_QUEUE = "booking.notifications";`) to keep producer/consumer DRY.
3. In `application-dev.properties`, add:
   ```
   # ActiveMQ (embedded, in-memory)
   spring.activemq.broker-url=vm://localhost?broker.persistent=false
   spring.activemq.in-memory=true
   ```
4. Run `mvn -q compile` to confirm dependency resolution and no compile errors.
5. Boot the app (`dev` profile) and confirm the embedded broker log line appears.

## Todo
- [x] Add `spring-boot-starter-activemq` + `activemq-broker` to `pom.xml`
- [x] Verify versions resolve (managed vs explicit)
- [x] Create `ActiveMQConfig.java` with `@EnableJms`, connection factory, `JmsTemplate`, queue bean + name constant
- [x] Set `setTrustAllPackages`/trusted packages for object serialization
- [x] Add ActiveMQ block to `application-dev.properties`
- [x] `mvn compile` clean
- [x] App boots with embedded broker log line

## Success criteria
- `mvn compile` passes.
- App starts in `dev`; log contains an ActiveMQ embedded broker start message.
- `booking.notifications` queue bean injectable (verified in phase 03).

## Risk assessment
| Risk | L | I | Mitigation |
|------|---|---|------------|
| `activemq-broker` version unmanaged by parent → build fail | Med | Med | Check `mvn dependency:tree`; pin explicit version if needed |
| Object message deserialization blocked by ActiveMQ trusted-packages security | Med | High | `setTrustAllPackages(true)` (dev) or restrict to messaging DTO package |

## Security considerations
- `setTrustAllPackages(true)` is acceptable for embedded/dev-only broker. Note it; tighten to specific package in prod if this ever leaves dev.

## Next steps
- Unblocks phase 03 (producer/consumer need `JmsTemplate` + queue name constant).
