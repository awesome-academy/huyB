---
feature_id: F001
slug: t2-messaging-activemq-rabbitmq
title: "Day 2 — Messaging: ActiveMQ + RabbitMQ"
lang: vi
status: draft
created: 2026-07-29
---

# Spec: Messaging — ActiveMQ + RabbitMQ (Day 2)

## Tổng quan

Tích hợp hai message broker vào hệ thống booking tours:

- **ActiveMQ (embedded):** gửi/nhận thông báo đặt tour (booking notification) → lưu vào DB
- **RabbitMQ (Docker):** fanout tour promotion đến nhiều consumer

## Phạm vi

### T2.1 — T2.5: ActiveMQ
- Cấu hình embedded ActiveMQ broker (`vm://localhost`)
- Flyway V7: bảng `notifications`
- `BookingNotificationProducer` → JmsTemplate → queue `booking.notifications`
- `BookingNotificationConsumer` → `@JmsListener` → `NotificationService.saveNotification()`
- Tích hợp vào `BookingServiceImpl.adminConfirmBooking()` và `adminCancelBooking()`

### T2.6 — T2.9: RabbitMQ
- Cấu hình RabbitMQ (`localhost:5672`)
- FanoutExchange `tour.promotions`, 2 queue: `tour.promo.notification.queue`, `tour.promo.log.queue`
- `TourPromotionPublisher` → gửi khi tour ACTIVE
- 2 listener: NotificationListener (lưu notification cho tất cả user) + LogListener (log INFO)
- Tích hợp vào `TourServiceImpl.create()` và `update()`

## Các quyết định thiết kế

- V6 đã tồn tại (seed_admin_user) → notifications bắt đầu từ **V7**
- `BookingNotificationMessage` implements `Serializable` (Java object serialization qua JMS)
- `TourPromotionMessage` serialized bằng Jackson2JsonMessageConverter (AMQP)
- `NotificationService.broadcastTourPromotion()` → batch insert cho tất cả user active
- Không có frontend (Day 2 chỉ là backend messaging)

## Acceptance criteria

| Task | Criterion |
|------|-----------|
| T2.1 | App khởi động, log ActiveMQ embedded broker xuất hiện |
| T2.2 | Flyway V7 áp dụng thành công; entity usable từ repository |
| T2.3 | Producer gửi message đến queue `booking.notifications` (manual verify; unit test với mock JmsTemplate deferred — technical debt) |
| T2.4 | Consume message → row được insert vào bảng `notifications` |
| T2.5 | Admin confirm booking → notification xuất hiện trong DB cho user đó |
| T2.6 | App khởi động với RabbitMQ connected |
| T2.7 | Message publish đến exchange không có lỗi |
| T2.8 | Tạo/kích hoạt tour → 2 listener nhận message; notification lưu; log xuất hiện |
| T2.9 | Admin create/activate tour → RabbitMQ consumers được trigger |

## Ngoài phạm vi

- WebSocket push (Day 3)
- Frontend notification bell (Day 3)
- `@Async` cho notification (Day 3)
