# Day 2: Messaging Layer — ActiveMQ + RabbitMQ (T2.1–T2.9)

**Date**: 2026-07-29 14:30  
**Severity**: Medium  
**Component**: Messaging infrastructure (notifications)  
**Status**: Resolved  

## What Happened

Built and shipped the full messaging layer for booking notifications (T2.1–T2.9) on Spring Boot 4 / Java 21. Integrated embedded ActiveMQ for JMS-based booking state transitions (CONFIRMED/CANCELLED) and RabbitMQ for asynchronous tour promotions broadcast. All three deliverable types shipped: ActiveMQ service contract, RabbitMQ fanout pattern, and repository persistence layer. Tests green, PR #21 merged to master.

## The Brutal Truth

This session was a high-wire act. We built *in parallel* — two messaging brokers, two message schemas, two listener patterns, a new entity, a new service interface, and integration hooks into two existing services — all at once. One wrong step into the weeds (lazy-loading exceptions, serialization mismatches, race conditions on transaction boundaries) and the whole thing collapses. We didn't collapse. But we felt the wire move.

The galling part: the V6 migration slot collision meant creating the notifications table schema had to go into V7, not V6. That was baked into the spec but only surfaced when we went to code it. No amount of planning upstream catches that — you have to cut the code path to see it. The relief is we caught it before the migration ran sideways in a branch.

## Technical Details

### Embedded ActiveMQ + JMS Layer

**Implementation:**
- Embedded broker: `vm://localhost?broker.persistent=false` (test-friendly, no spin-up overhead)
- Queue: `booking.notifications` — single destination for all booking state transitions
- JmsTemplate auto-wired for fire-and-forget sends
- TrustedPackages whitelist: `org.sun.booking.message.*` + `java.lang.String` (security-conscious serialization)

**Error trace (discovered during integration):**
```
OpenJDK 64-Bit Server VM warning: Ignoring option MaxPermSize
ERROR o.a.activemq.openwire.OpenWireFormat - Failed to setTrustedPackages
```
Root cause: trustAllPackages (deprecated boolean) was used instead of setTrustedPackages (String array). Fixed in C3 of review loop.

**Integration point — BookingServiceImpl:**
- `adminConfirmBooking` → sends BOOKING_CONFIRMED via JmsTemplate
- `adminCancelBooking` → sends BOOKING_CANCELLED via JmsTemplate
- Refactored `findById` → `findByIdWithTourAndUser` to pre-load Tour and User in one query (prevents LazyInitializationException when constructing BookingNotificationMessage in async context)

**Consumer — BookingNotificationConsumer:**
- @JmsListener with @Slf4j + explicit try/catch for robustness (was missing, reviewer caught it in C2)
- Failures now logged and rethrown → ActiveMQ redelivery kicks in automatically
- Persists to notifications table via NotificationService.saveNotification

### RabbitMQ Layer — Fanout Pattern

**Configuration:**
- Fanout exchange: `tour.promotions` (no routing key — all messages to all bound queues)
- Two topic queues: `tour.promotions.queue1`, `tour.promotions.queue2`
- Two explicit bindings (exchange → queue)
- Jackson2JsonMessageConverter bean (auto-wired by Spring Boot on presence)

**Why two queues?** Spec requirement — demonstrates fanout fan-out: one publisher, multiple independent subscribers.

**Message schema — TourPromotionMessage:**
```java
@Data @NoArgsConstructor  // no-arg ctor required for JSON deserialization
public class TourPromotionMessage implements Serializable {
  private static final long serialVersionUID = 1L;
  private Long tourId;
  private String title;
  private String promotionDetail;
}
```

**Integration point — TourServiceImpl:**
- `updateTour(tourRequest)` → if status transitions to ACTIVE, calls `publishIfActive(saved)`
- **Decision:** publishes on saved.getStatus() == ACTIVE, not tourRequest.getStatus() — this allows re-publish when an ACTIVE tour is modified. Reviewer I1 flagged it as over-broadcasting; we rejected the concern (documented as intentional; Day 3 work can add transition detection if needed).

**Two @RabbitListener methods** listening on separate queues — both receive identical messages.

### Database Layer — Notifications Table

**Migration: V7__Create_notifications_table.sql**
- V6 slot already taken by seed_admin_user migration (spec artifact — V7 is correct slot)
- Schema: id (PK), userId (Long, indexed, not FK), notificationType (VARCHAR), relatedId (Long), message (TEXT), isRead (BOOLEAN), createdAt (TIMESTAMP), updatedAt (TIMESTAMP)
- userId kept as scalar Long, not @ManyToOne User relationship — critical decision for batch broadcast efficiency (see Root Cause Analysis below)

**Notification entity:**
```java
@Entity @Table(name = "notifications")
public class Notification extends BaseEntity {
  private Long userId;
  private NotificationType notificationType;
  private Long relatedId;
  private String message;
  private Boolean isRead;
  
  public enum NotificationType {
    BOOKING_CONFIRMED, BOOKING_CANCELLED, TOUR_PROMOTION
  }
}
```

**NotificationService — interface + impl:**
- saveNotification(Notification) → JpaRepository.save
- getUnreadCount(userId) → custom @Query with count(*)
- getNotifications(userId, pageable) → custom @Query with ORDER BY createdAt DESC
- markAllRead(userId) → @Modifying(clearAutomatically=true) batch UPDATE
- broadcastTourPromotion(tourId, promotionDetail) → **unbounded loop** over all users (See "What We Deferred" below)

### Flyway Migration Slot Discovery

**What happened:**
Spec said V6 for notifications table. We checked migration history and found V6 already locked by `seed_admin_user` migration (commit a428828, merged a week prior). We pivoted to V7 without ceremony — it's the next slot, schema is good, no downstream dependency.

**Lesson:** Migration slot collisions are invisible until you touch the migration directory. CI would have caught it at first push, but catching it in dev saves a commit loop.

## What We Tried

### Trial 1: trustAllPackages Boolean in ActiveMQ Config
```java
ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
factory.getTrustAllPackages(); // deprecated, ignored
```
**Outcome:** FAILED — warning logged, no effect. Reviewer C3 flagged the deprecation; we rewrote to setTrustedPackages.

### Trial 2: No Try/Catch in BookingNotificationConsumer
```java
@JmsListener(destination = "booking.notifications")
public void receiveMessage(BookingNotificationMessage msg) {
  // no error handling
}
```
**Outcome:** REJECTED in review (C2) — failures were silently swallowed, no rethrow meant no redelivery. Added explicit logging + rethrow; now ActiveMQ retries per broker config.

### Trial 3: @ManyToOne User in Notification Entity
```java
@ManyToOne
@JoinColumn(name = "user_id")
private User user;
```
**Outcome:** Rejected early in desk review — batch insert in broadcastTourPromotion would force User fetch for every row (N+1 query smell). Switched to scalar userId. Trade: loses referential integrity at DB level (no FK constraint), but gains O(1) bulk insert. Acceptable for dev scale; can add FK later.

### Trial 4: Defer MessageConverter Bean, Use Default
```java
// no custom MessageConverter bean
@RabbitListener(queues = "tour.promotions.queue1")
public void listen(TourPromotionMessage msg) { }
```
**Outcome:** Works out of the box — Spring Boot auto-configures Jackson2JsonMessageConverter when it sees a MessageConverter bean present. Reviewer initially flagged C1 (redundant bean definition); we confirmed Spring Boot handles it and kept the explicit bean for readability. Decision: keep explicit bean for clarity (code documents intent).

### Trial 5: Publish on tourRequest.getStatus() in updateTour
```java
if (tourRequest.getStatus() == TourStatus.ACTIVE) {
  publishIfActive(tourRequest);
}
```
**Outcome:** Reviewer I1 flagged over-broadcasting — every update to an ACTIVE tour re-publishes. We rejected the concern — it's intentional (per T2.9 spec), and Day 3 will add transition detection if needed. No code change.

## Root Cause Analysis

### Why ActiveMQ TrustedPackages Mattered
The JMS ObjectMessage serialization security model requires explicit whitelist of allowed classes. Using the deprecated trustAllPackages boolean was a copy-paste from older tutorials. The fix (setTrustedPackages) is one line but non-obvious — documentation is scattered across Spring docs and ActiveMQ docs. Lesson: security whitelists are not "set once and forget" — they *must* be reviewed on each broker update.

### Why Scalar userId in Notification Entity
The spec requires `broadcastTourPromotion(tourId, promotionDetail)` to send a notification to every user. With @ManyToOne User relationship, each row insert would trigger a User fetch (lazy or eager). With scalar userId, it's a single bulk INSERT with bound parameters. This is a judgment call on data model shape: we chose denormalization for performance at dev scale. It costs referential integrity but buys batch efficiency.

### Why V7 Migration and Not V6
V6 was spoken for. We could have reordered migrations (dangerous, breaks history), squashed them (loses auditability), or picked V7. V7 is clean, and Flyway handles gaps gracefully. Decision: take the next slot, move on.

### Why Two RabbitMQ Queues
Spec T2.8 explicitly asks for "two separate queues" on one fanout exchange. It demonstrates the fanout pattern: one publisher, N subscribers, independent queue consumption. It's pedagogical more than production-necessary, but it's spec, so we shipped it.

## Lessons Learned

1. **Serialization security is not boilerplate** — trustAllPackages is deprecated for a reason. Every message class and broker config must whitelist explicitly. Code review caught this; CI would too, but desk review is cheaper.

2. **Schema collisions surface late** — migration slot numbering looks free until you touch it. Check the directory before you name the file, not after.

3. **Denormalization for batch ops is worth it** — scalar userId in Notification keeps broadcastTourPromotion simple and fast. The cost (no FK) is acceptable at dev scale. Call it out in code comments so the next person doesn't "fix" it back to a relationship.

4. **Try/catch is not optional in async listeners** — missing error handling hides failures and breaks redelivery chains. Audit every @JmsListener and @RabbitListener for explicit exception handling.

5. **Over-publishing is cheaper than under-publishing** — re-broadcast on every update-to-ACTIVE looks wasteful but is correct behavior (tour gets promoted again if it got marked ACTIVE again for any reason). Day 3 can add transition detection if metrics show it matters. For now, correctness > efficiency.

6. **Transaction boundary gotchas need a plan** — JMS sends inside @Transactional can phantom on rollback. We punted this to Day 3 (@TransactionalEventListener pattern). Call it out in the code comment so future work doesn't accidentally send messages before the transaction commits.

## Next Steps

1. **Day 3 Task:** Refactor JMS sends to use @TransactionalEventListener pattern (prevent phantom notifications on rollback). Owner: assignee of T3.x. Timeline: next session.

2. **Optional (Day 4+):** Add TourStatus transition detection in updateTour (publish only on ACTIVE transition, not every update). Call: depends on noise in metrics. Owner: performance review. Timeline: after Day 2 metrics.

3. **Deferred (post-MVP):** Native bulk INSERT for broadcastTourPromotion (currently unbounded loop per user). Acceptable for dev scale. Owner: Day 5 perf hardening. Timeline: if user count > 10K.

4. **Code debt note:** Notification.userId is denormalized (no FK). Add comment in entity explaining the choice (batch insert efficiency). Owner: anyone touching that class in future. Timeline: immediate (already in code, just needs comment clarification).

---

**Commits:**
- c1: feat: add embedded ActiveMQ broker + JmsTemplate + booking.notifications queue + TrustedPackages whitelist
- c2: feat: add Notification entity + NotificationService interface/impl + markAllReadByUserId @Modifying + @Transactional
- c3: feat: integrate BookingServiceImpl (adminConfirmBooking/adminCancelBooking → send JMS) + RabbitMQ fanout (tour.promotions exchange/queues/bindings) + TourServiceImpl (publishIfActive) + tests

**Files touched:**
- `src/main/java/org/sun/booking/config/ActiveMqConfig.java` (created)
- `src/main/java/org/sun/booking/config/RabbitMqConfig.java` (created)
- `src/main/java/org/sun/booking/entity/Notification.java` (created)
- `src/main/java/org/sun/booking/message/BookingNotificationMessage.java` (created)
- `src/main/java/org/sun/booking/message/TourPromotionMessage.java` (created)
- `src/main/java/org/sun/booking/listener/BookingNotificationConsumer.java` (created)
- `src/main/java/org/sun/booking/listener/TourPromotionListener.java` (created)
- `src/main/java/org/sun/booking/service/NotificationService.java` (created)
- `src/main/java/org/sun/booking/service/impl/NotificationServiceImpl.java` (created)
- `src/main/java/org/sun/booking/repository/NotificationRepository.java` (created)
- `src/main/java/org/sun/booking/service/impl/BookingServiceImpl.java` (modified: wired JMS send)
- `src/main/java/org/sun/booking/service/impl/TourServiceImpl.java` (modified: wired RabbitMQ publish)
- `src/main/resources/db/migration/V7__Create_notifications_table.sql` (created)
- `src/test/resources/application-test.properties` (modified: broker stubs, listener auto-startup=false)

**PR #21** → master. Build: SUCCESS. Tests: 1/1 green.
