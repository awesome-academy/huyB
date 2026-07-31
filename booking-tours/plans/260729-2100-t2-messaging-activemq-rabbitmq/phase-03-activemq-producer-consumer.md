# Phase 03 — ActiveMQ Producer/Consumer + NotificationService (T2.3, T2.4)

## Context links
- Spec: `../spec/t2-messaging.md`
- Plan: `plan.md`
- Depends on: phase 01 (JmsTemplate + queue constant), phase 02 (repository + entity)

## Overview
- **Priority:** P2
- **Status:** completed
- **Depends on:** 01, 02
- Message DTO + producer (send) + consumer (`@JmsListener`) + `NotificationService` that persists. This is the working ActiveMQ pipeline end to end (minus the booking-service trigger, which is phase 04).

## Key insights
- `BookingNotificationMessage` must implement `Serializable` — JMS object message default serialization. Give it a `serialVersionUID`.
- Consumer runs on a JMS listener thread OUTSIDE any web request/tx → `saveNotification` must open its own `@Transactional`.
- Keep the queue name in ONE place: reference `ActiveMQConfig.BOOKING_NOTIFICATIONS_QUEUE` from phase 01 (DRY). `@JmsListener(destination = ...)` needs a compile-time constant → use the `public static final String`.
- `NotificationService` is the shared write API used by BOTH brokers; design it now to also carry `broadcastTourPromotion` (used in phase 06) so phase 06 does not reopen this interface.

## Requirements
**Functional**
- `BookingNotificationMessage(userId, type, title, message)` serializable.
- `BookingNotificationProducer.sendNotification(msg)` → sends object message to `booking.notifications`.
- `BookingNotificationConsumer` `@JmsListener` → `NotificationService.saveNotification(...)`.
- `NotificationService` interface + impl:
  - `saveNotification(Long userId, NotificationType type, String title, String message)`
  - `long getUnreadCount(Long userId)`
  - `Page<NotificationDto> getNotifications(Long userId, Pageable pageable)`
  - `void markAllRead(Long userId)`
  - `void broadcastTourPromotion(Long tourId, String tourTitle)` (impl body used by phase 06)

**Non-functional**
- Consumer idempotent enough for dev: at-least-once delivery acceptable; duplicates create duplicate rows (documented, out of scope to dedupe).

## Architecture / data flow
```
sendNotification(BookingNotificationMessage)
  → jmsTemplate.convertAndSend(QUEUE, message)  [object serialization]
  → broker "booking.notifications"
  → @JmsListener onMessage(BookingNotificationMessage)  [listener thread]
  → notificationService.saveNotification(userId, type, title, message)  [@Transactional]
  → notificationRepository.save(Notification)
```

## Related code files
**Create**
- `messaging/activemq/BookingNotificationMessage.java`
- `messaging/activemq/BookingNotificationProducer.java`
- `messaging/activemq/BookingNotificationConsumer.java`
- `service/NotificationService.java`
- `service/impl/NotificationServiceImpl.java`

## Implementation steps
1. Create `messaging/activemq/BookingNotificationMessage.java`:
   - `implements Serializable`, `private static final long serialVersionUID = 1L;`
   - Fields: `Long userId; NotificationType type; String title; String message;`
   - Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`. (Reference `Notification.NotificationType`.)
2. Create `service/NotificationService.java` (interface) with the 5 methods above.
3. Create `service/impl/NotificationServiceImpl.java`:
   - `@Service @RequiredArgsConstructor`, inject `NotificationRepository` (+ `UserRepository` for `broadcastTourPromotion`).
   - `saveNotification(...)` `@Transactional`: build `Notification` (isRead=false), `save`.
   - `getUnreadCount` `@Transactional(readOnly=true)`: delegate to repo.
   - `getNotifications` `@Transactional(readOnly=true)`: repo page → map to `NotificationDto`.
   - `markAllRead` `@Transactional`: load unread page or use a bulk `@Modifying` update (keep simple: fetch + set — or add repo bulk update; prefer bulk update method to avoid loading rows).
   - `broadcastTourPromotion(tourId, tourTitle)` `@Transactional`: fetch all active users' ids (`userRepository` — add a lightweight `findAllByIsActiveTrue()` or select ids), build one `Notification` per user (type `TOUR_PROMOTION`, title/message from `tourTitle`), `repository.saveAll(list)`. Add the `findAllByIsActiveTrue()` (or id-projection) method to `UserRepository`.
4. Create `messaging/activemq/BookingNotificationProducer.java`:
   - `@Component @RequiredArgsConstructor`, inject `JmsTemplate`.
   - `sendNotification(BookingNotificationMessage msg)` → `jmsTemplate.convertAndSend(ActiveMQConfig.BOOKING_NOTIFICATIONS_QUEUE, msg);`
5. Create `messaging/activemq/BookingNotificationConsumer.java`:
   - `@Component @RequiredArgsConstructor`, inject `NotificationService`.
   - `@JmsListener(destination = ActiveMQConfig.BOOKING_NOTIFICATIONS_QUEUE)` `void onMessage(BookingNotificationMessage msg)` → `notificationService.saveNotification(msg.getUserId(), msg.getType(), msg.getTitle(), msg.getMessage());`
6. `mvn compile`; boot app; (defer live trigger to phase 04).

## Todo
- [x] `BookingNotificationMessage` (Serializable + serialVersionUID)
- [x] `NotificationService` interface (5 methods incl. `broadcastTourPromotion`)
- [x] `NotificationServiceImpl` with own `@Transactional` boundaries
- [x] Add `findAllByIsActiveTrue()` (or id projection) to `UserRepository`
- [x] `BookingNotificationProducer` using shared queue constant
- [x] `BookingNotificationConsumer` `@JmsListener` using shared queue constant
- [x] `mvn compile` clean

## Success criteria (T2.3, T2.4)
- `mvn compile` passes.
- Code inspection: `sendNotification` delegates to `jmsTemplate.convertAndSend(BOOKING_NOTIFICATIONS_QUEUE, msg)`.
- **Deferred (tech debt):** Unit test với mock JmsTemplate xác nhận `sendNotification` gọi đúng destination; integration test producer→consumer→notifications row. Chưa có test tự động — T2.3 verified bằng code inspection, không phải automated test.

## Risk assessment
| Risk | L | I | Mitigation |
|------|---|---|------------|
| Consumer tx missing → row not committed | Med | High | `saveNotification` explicitly `@Transactional` |
| Deserialization blocked (trusted packages) | Med | High | Handled in phase 01 config |
| `@JmsListener` destination needs constant, not expression | Low | Med | Use `public static final String` constant (SpEL not needed) |
| `broadcastTourPromotion` here but triggered in phase 06 — dead until then | Low | Low | Intentional: define API once (DRY), covered by phase 06 test |

## Security considerations
- No external input on this path yet (message built server-side in phase 04). Trusted-packages restricted in phase 01.

## Rollback
- Delete the 5 files; revert `UserRepository` addition. No schema change here.

## Next steps
- Unblocks phase 04 (BookingService injects producer) and phase 06 (reuses `broadcastTourPromotion`).
