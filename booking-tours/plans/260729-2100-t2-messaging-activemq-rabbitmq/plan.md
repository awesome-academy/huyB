---
title: "Day 2 — Messaging: ActiveMQ + RabbitMQ"
description: "Embedded ActiveMQ booking notifications + RabbitMQ fanout tour promotions, persisted to a new notifications table."
status: completed
priority: P2
effort: 8h
branch: task_98829
tags: [messaging, activemq, rabbitmq, jms, amqp, notifications, flyway]
created: 2026-07-29
---

# Day 2 — Messaging: ActiveMQ + RabbitMQ

Backend-only messaging integration for SUN Booking Tours. Two brokers, one shared
`notifications` table.

- **ActiveMQ (embedded, `vm://localhost`):** booking confirm/cancel → JMS queue → consumer persists a notification row for that user.
- **RabbitMQ (Docker):** tour create/activate → fanout exchange → 2 queues → notification broadcast to all active users + INFO log.

No frontend, no WebSocket, no `@Async` (all deferred to Day 3).

## Architecture at a glance

```
ActiveMQ (point-to-point)
  BookingServiceImpl.adminConfirm/Cancel ──▶ BookingNotificationProducer
     ──JmsTemplate──▶ queue "booking.notifications"
     ──@JmsListener──▶ BookingNotificationConsumer ──▶ NotificationService.saveNotification()
     ──▶ INSERT 1 row (notifications)

RabbitMQ (publish/subscribe, fanout)
  TourServiceImpl.create/update (status=ACTIVE) ──▶ TourPromotionPublisher
     ──RabbitTemplate──▶ FanoutExchange "tour.promotions"
     ├─▶ queue "tour.promo.notification.queue" ──@RabbitListener──▶ NotificationService.broadcastTourPromotion() ──▶ batch INSERT (all active users)
     └─▶ queue "tour.promo.log.queue" ─────────@RabbitListener──▶ log.info(...)
```

## Phases

| # | Phase | Task | Status | Depends on |
|---|-------|------|--------|------------|
| 01 | ActiveMQ config & dependencies | T2.1 | completed | — |
| 02 | Notification entity + Flyway V7 + repository + DTO | T2.2 | completed | — |
| 03 | ActiveMQ producer/consumer + NotificationService | T2.3, T2.4 | completed | 01, 02 |
| 04 | Integrate producer into BookingService | T2.5 | completed | 03 |
| 05 | RabbitMQ config & dependencies | T2.6 | completed | — |
| 06 | RabbitMQ publisher + 2 listeners | T2.7, T2.8 | completed | 02, 03, 05 |
| 07 | Integrate publisher into TourService | T2.9 | completed | 06 |

**Parallelism:** Phases 01, 02, 05 have no dependencies and can run first in any order.
Phase 03 needs 01+02. Phase 06 needs 02+03+05 (reuses `NotificationService`). Phases 04
and 07 are the integration tails.

## Dependency graph

```
01 ─┐
    ├─▶ 03 ─▶ 04
02 ─┤        │
    └────────┼─▶ 06 ─▶ 07
05 ──────────┘
```

## Key decisions

- Notifications table = **V7** (V6 = seed_admin_user already taken).
- `BookingNotificationMessage` → `Serializable` (JMS object message).
- `TourPromotionMessage` → Jackson JSON via `Jackson2JsonMessageConverter` (AMQP).
- Consumers persist inside their own `@Transactional` boundary (listener thread ≠ producer thread).
- RabbitMQ listeners must NOT start if broker is down at boot — accept default fail-fast; document Docker prerequisite.
- Notification title/message text: Vietnamese, matching project convention.

## Risks (High only — see phase files for full matrix)

| Risk | Likelihood | Impact | Countermove |
|------|-----------|--------|-------------|
| RabbitMQ not running → app boot fails | Med | High | Document `docker run` prerequisite in phase 05; listeners fail-fast is acceptable for dev |
| Producer runs in booking @Transactional, message sent before commit | Med | High | Send AFTER `save()` returns within same tx; consumer is async so ordering is fine. Do NOT block on consumer. Note: tx-not-committed edge is acceptable for dev scope |
| `broadcastTourPromotion` N+1 insert on large user base | Low | Med | Use `saveAll()` batch; scope is small dev dataset |

## Rollback

Each phase is additive. To undo: revert the phase's new files + remove the injected
call in the service (phases 04, 07). Flyway V7 rollback = manual `DROP TABLE notifications`
(Flyway Community has no undo) — documented in phase 02.

## Success criteria (observable)

- App boots with embedded ActiveMQ log line and RabbitMQ "connected" log.
- Admin confirm booking → 1 row in `notifications` (type `BOOKING_CONFIRMED`) for that user.
- Admin cancel booking → 1 row (`BOOKING_CANCELLED`).
- Create/activate ACTIVE tour → N rows (`TOUR_PROMOTION`, one per active user) + INFO log line.
- Unit tests: producer sends to correct destination; consumer persists correctly.
- `mvn compile` clean; `mvn test` green.

## Phase files

- [phase-01-activemq-config.md](phase-01-activemq-config.md)
- [phase-02-notification-entity.md](phase-02-notification-entity.md)
- [phase-03-activemq-producer-consumer.md](phase-03-activemq-producer-consumer.md)
- [phase-04-booking-integration.md](phase-04-booking-integration.md)
- [phase-05-rabbitmq-config.md](phase-05-rabbitmq-config.md)
- [phase-06-rabbitmq-publisher-listeners.md](phase-06-rabbitmq-publisher-listeners.md)
- [phase-07-tour-integration.md](phase-07-tour-integration.md)

## Unresolved questions

See end of individual phase files; consolidated list in `plan.md` review is empty at draft
time except: (1) exact Vietnamese wording of notification titles — using sensible defaults;
(2) whether prod profile also needs RabbitMQ config — scoped to dev only per spec.
