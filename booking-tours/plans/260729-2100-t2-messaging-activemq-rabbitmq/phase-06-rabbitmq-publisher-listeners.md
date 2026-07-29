# Phase 06 — TourPromotionPublisher + 2 Listeners (T2.7, T2.8)

## Context links
- Spec: `../spec/t2-messaging.md`
- Plan: `plan.md`
- Depends on: phase 02 (repository), phase 03 (`NotificationService.broadcastTourPromotion`), phase 05 (exchange/queues/template)

## Overview
- **Priority:** P2
- **Status:** completed
- **Depends on:** 02, 03, 05
- JSON message DTO + publisher (send to fanout) + two `@RabbitListener`s: one broadcasts a `TOUR_PROMOTION` notification to all active users, one logs INFO. End-to-end RabbitMQ pipeline minus the tour-service trigger (phase 07).

## Key insights
- `TourPromotionMessage` serialized as JSON (`Jackson2JsonMessageConverter` from phase 05) → needs a no-arg constructor + getters/setters for Jackson (Lombok `@NoArgsConstructor @AllArgsConstructor`).
- Publisher sends to the EXCHANGE (fanout), not a queue: `rabbitTemplate.convertAndSend(EXCHANGE, "", message)` — routing key ignored by fanout, pass `""`.
- Both listeners receive the same message (fanout). Listener threads run outside web tx → the notification listener relies on `NotificationService.broadcastTourPromotion` opening its own `@Transactional` (already designed in phase 03).
- `broadcastTourPromotion` batch-inserts one row per active user via `saveAll` — reuse phase 03 impl; do NOT reimplement here (DRY).
- Reference queue names via `RabbitMQConfig.PROMO_NOTIFICATION_QUEUE` / `PROMO_LOG_QUEUE` constants in `@RabbitListener(queues = ...)`.

## Requirements
**Functional**
- `TourPromotionMessage(tourId, tourTitle)` JSON-serializable.
- `TourPromotionPublisher.publishNewTour(msg)` → send to `tour.promotions` fanout.
- `TourPromotionNotificationListener` `@RabbitListener(PROMO_NOTIFICATION_QUEUE)` → `notificationService.broadcastTourPromotion(tourId, tourTitle)`.
- `TourPromotionLogListener` `@RabbitListener(PROMO_LOG_QUEUE)` → `log.info(...)`.

**Non-functional**
- Broadcast uses a single `saveAll` batch.

## Architecture / data flow
```
publishNewTour(TourPromotionMessage)
  → rabbitTemplate.convertAndSend("tour.promotions", "", msg)  [JSON]
  → fanout ─┬─▶ tour.promo.notification.queue ─▶ NotificationListener ─▶ broadcastTourPromotion() ─▶ saveAll(N rows)
            └─▶ tour.promo.log.queue          ─▶ LogListener        ─▶ log.info("New tour promotion: " + title)
```

## Related code files
**Create**
- `messaging/rabbitmq/TourPromotionMessage.java`
- `messaging/rabbitmq/TourPromotionPublisher.java`
- `messaging/rabbitmq/TourPromotionNotificationListener.java`
- `messaging/rabbitmq/TourPromotionLogListener.java`

**Reuse (no change)**
- `service/NotificationService.broadcastTourPromotion(Long, String)` (defined phase 03)

## Implementation steps
1. Create `messaging/rabbitmq/TourPromotionMessage.java`:
   - Fields `Long tourId; String tourTitle;`
   - Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` (no-arg ctor required for Jackson).
2. Create `messaging/rabbitmq/TourPromotionPublisher.java`:
   - `@Component @RequiredArgsConstructor`, inject `RabbitTemplate`.
   - `publishNewTour(TourPromotionMessage msg)` → `rabbitTemplate.convertAndSend(RabbitMQConfig.TOUR_PROMOTIONS_EXCHANGE, "", msg);`
3. Create `messaging/rabbitmq/TourPromotionNotificationListener.java`:
   - `@Component @RequiredArgsConstructor` (+ `@Slf4j` optional), inject `NotificationService`.
   - `@RabbitListener(queues = RabbitMQConfig.PROMO_NOTIFICATION_QUEUE)` `void onMessage(TourPromotionMessage msg)` → `notificationService.broadcastTourPromotion(msg.getTourId(), msg.getTourTitle());`
4. Create `messaging/rabbitmq/TourPromotionLogListener.java`:
   - `@Component @Slf4j`.
   - `@RabbitListener(queues = RabbitMQConfig.PROMO_LOG_QUEUE)` `void onMessage(TourPromotionMessage msg)` → `log.info("New ACTIVE tour published: id={}, title={}", msg.getTourId(), msg.getTourTitle());`
5. `mvn compile`; boot app (RabbitMQ up); publish a test message (temporary REST hook or a test) → confirm both listeners fire, notification rows inserted for active users, log line printed.

## Todo
- [x] `TourPromotionMessage` (Jackson-friendly, no-arg ctor)
- [x] `TourPromotionPublisher` sending to fanout exchange (routing key `""`)
- [x] `TourPromotionNotificationListener` → `broadcastTourPromotion`
- [x] `TourPromotionLogListener` → `log.info`
- [x] `mvn compile` clean
- [x] Both listeners fire on one publish; rows + log verified

## Success criteria (T2.7, T2.8)
- Publish → no error at publisher (T2.7).
- One publish → both listeners receive it; N `TOUR_PROMOTION` rows (N = active users) + one INFO log line (T2.8).
- Unit test: `publishNewTour` calls `rabbitTemplate.convertAndSend` with exchange `tour.promotions` (mock template).

## Risk assessment
| Risk | L | I | Mitigation |
|------|---|---|------------|
| JSON deserialize fails (no default ctor) | Med | High | `@NoArgsConstructor` present; shared converter bean |
| Listener tx missing → rows not committed | Med | High | `broadcastTourPromotion` is `@Transactional` (phase 03) |
| N+1 / large broadcast | Low | Med | `saveAll` batch; small dev dataset |
| Duplicate delivery on requeue | Low | Low | Accepted for dev; no dedupe (YAGNI) |

## Security considerations
- Message content server-generated (tour id/title). No user input on this path.

## Rollback
- Delete the 4 files. `broadcastTourPromotion` stays (harmless, unused). No schema change.

## Next steps
- Unblocks phase 07 (TourService injects publisher).
