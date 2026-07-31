# Phase 04 — Integrate Producer into BookingService (T2.5)

## Context links
- Spec: `../spec/t2-messaging.md`
- Plan: `plan.md`
- Target: `service/impl/BookingServiceImpl.java` (lines ~190–232: `adminConfirmBooking`, `adminCancelBooking`)
- Depends on: phase 03 (producer + message DTO)

## Overview
- **Priority:** P2
- **Status:** completed
- **Depends on:** 03
- Fire a booking notification after admin confirm/cancel. Minimal, surgical edit to an existing service — no new files.

## Key insights
- Both target methods are `@Transactional` and already call `bookingRepository.save(booking)`. Inject the producer send AFTER the save (and after payment update) so message reflects the final state.
- `Booking` exposes `getUser()` and `getTour()` (Lombok `@Getter`). Need `booking.getUser().getId()` and `booking.getTour().getTitle()` for message content. These are lazy — accessed inside the tx, so proxies resolve fine.
- Message is sent inside the tx: the message hits the broker immediately, but the consumer persists asynchronously on its own thread. For dev scope this ordering is acceptable (see plan risk table). Do NOT introduce `@TransactionalEventListener` — YAGNI for Day 2; Day 3 (`@Async`) can revisit.
- Add exactly one dependency to the constructor via the existing `@RequiredArgsConstructor` (`private final BookingNotificationProducer ...`).

## Requirements
**Functional**
- `adminConfirmBooking` → send `BOOKING_CONFIRMED` notification for `booking.user.id`.
- `adminCancelBooking` → send `BOOKING_CANCELLED` notification for `booking.user.id`.
- Title/message: Vietnamese, referencing the tour title and booking code.

**Non-functional**
- No behavior change to existing confirm/cancel logic; messaging is additive.

## Architecture / data flow
```
adminConfirmBooking(id)
  ... existing save + payment update ...
  → producer.sendNotification(BookingNotificationMessage{
        userId = booking.getUser().getId(),
        type = BOOKING_CONFIRMED,
        title = "Đặt tour đã được xác nhận",
        message = "Booking " + booking.getBookingCode() + " cho tour \"" + booking.getTour().getTitle() + "\" đã được xác nhận." })
```
Same shape for cancel with `BOOKING_CANCELLED` and a cancel message.

## Related code files
**Modify**
- `src/main/java/com/sunasterisk/bookingtours/service/impl/BookingServiceImpl.java`

## Implementation steps
1. Add import for `BookingNotificationProducer`, `BookingNotificationMessage`, and `Notification.NotificationType`.
2. Add field `private final BookingNotificationProducer bookingNotificationProducer;` (picked up by `@RequiredArgsConstructor`).
3. In `adminConfirmBooking`, after the `paymentRepository` update block, build and send a `BOOKING_CONFIRMED` message (userId from `booking.getUser().getId()`, title/message per data-flow above).
4. In `adminCancelBooking`, after the payment `FAILED` update block, build and send a `BOOKING_CANCELLED` message.
5. Extract a small private helper `sendBookingNotification(Booking booking, NotificationType type, String title)` if the two call sites share structure (DRY) — optional, only if it reduces duplication cleanly.
6. `mvn compile`; run app; admin-confirm a PENDING booking via existing admin flow; verify a `notifications` row (type `BOOKING_CONFIRMED`) for that user; repeat cancel.

## Todo
- [x] Import + inject `BookingNotificationProducer`
- [x] Send `BOOKING_CONFIRMED` in `adminConfirmBooking` after save
- [x] Send `BOOKING_CANCELLED` in `adminCancelBooking` after save
- [x] (Optional) private helper to avoid duplication
- [x] `mvn compile` clean
- [x] Manual: confirm → row present; cancel → row present

## Success criteria (T2.5)
- Admin confirm booking → exactly one `notifications` row (`BOOKING_CONFIRMED`) for the booking's user.
- Admin cancel booking → exactly one `notifications` row (`BOOKING_CANCELLED`).
- Existing booking confirm/cancel tests still pass; `mvn test` green.

## Risk assessment
| Risk | L | I | Mitigation |
|------|---|---|------------|
| Lazy `getUser()/getTour()` LazyInitException | Low | Med | Accessed inside existing `@Transactional` method — safe |
| Message sent then tx rolls back → consumer persists notification for a non-committed booking | Low | Med | Confirm/cancel rarely roll back post-save; acceptable for dev. Documented |
| Duplicate injection breaks constructor | Low | High | `@RequiredArgsConstructor` regenerates — verify compile |

## Security considerations
- Admin-only entry points (existing authorization unchanged). No new input surface.

## Rollback
- Remove the injected field + the two send blocks (and helper). Pure revert of one file.

## Next steps
- ActiveMQ pipeline complete after this phase. Independent of RabbitMQ track.
