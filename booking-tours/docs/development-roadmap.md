# Development Roadmap — SUN Booking Tours

**Project:** SUN Booking Tours  
**Stack:** Spring Boot 4.0.6 · Java 21 · Spring Security 6 · JWT · OAuth2 · Thymeleaf · MySQL  
**Last updated:** 2026-08-03

---

## Sprint Overview

Advanced sprint extending the v1 platform with 12 enterprise-grade features over 5 days.

| Day | Theme | Status |
|-----|-------|--------|
| Day 1 | Foundation & Infrastructure | Complete |
| Day 2 | Messaging: ActiveMQ + RabbitMQ | Complete |
| Day 3 | Realtime (WebSocket/STOMP) + Scheduler | Complete |
| Day 4 | File Handling, Multithread & SOAP | Complete |
| Day 5 | Charts, Testing & Polish | Pending |

---

## Day 1 — Foundation & Infrastructure

| Task | Description | Status |
|------|-------------|--------|
| T1.1 | PostgreSQL → MySQL migration (Flyway V1–V5 rewritten) | Done |
| T1.2 | SLF4J + Logback config, MdcLoggingFilter (requestId/userEmail) | Done |
| T1.3 | Swagger / OpenAPI 3.0 (springdoc 2.6.0, 14 controllers annotated) | Done |
| T1.4 | OAuth2: Facebook + Twitter (`CustomStandardOAuth2UserService`) | Done (E2E pending real credentials) |

---

## Day 2 — Messaging: ActiveMQ + RabbitMQ

| Task | Description | Status |
|------|-------------|--------|
| T2.1 | ActiveMQ embedded broker config | Done |
| T2.2 | `notifications` table (Flyway V7), `Notification` entity | Done |
| T2.3 | `BookingNotificationProducer` — sends to `booking.notifications` queue | Done |
| T2.4 | `BookingNotificationConsumer` + `NotificationService` | Done |
| T2.5 | Integrate producer into `BookingServiceImpl` (confirm/cancel hooks) | Done |
| T2.6 | RabbitMQ AMQP config (fanout exchange `tour.promotions`) | Done |
| T2.7 | `TourPromotionPublisher` | Done |
| T2.8 | `TourPromotionNotificationListener` + `TourPromotionLogListener` | Done |
| T2.9 | Integrate publisher into `TourServiceImpl` (ACTIVE status hook) | Done |

---

## Day 3 — Realtime + Scheduler

| Task | Description | Status |
|------|-------------|--------|
| T3.1 | WebSocket + STOMP config (`/ws` endpoint, SockJS fallback) | Done |
| T3.2 | `NotificationController` (REST + STOMP push via `SimpMessagingTemplate`) | Done |
| T3.3 | Frontend WebSocket client (`notification.js`, SockJS + StompJS CDN) | Done |
| T3.4 | Notification bell UI in navbar, `/profile/notifications` page | Done |
| T3.5 | `AutoCompleteBookingJob` cron 00:30, `scheduled_job_logs` table (V8) | Done |
| T3.6 | `PendingPaymentCleanupJob` cron 01:00 | Done |
| T3.7 | `AsyncConfig` — `notificationExecutor` + `importExecutor` beans | Done |

---

## Day 4 — File Handling, Multithread & SOAP

| Task | Description | Status |
|------|-------------|--------|
| T4.1 | Apache POI dep + `BookingExcelExporter` (styled header #BDD7EE, alternating rows) | Done |
| T4.2 | `GET /admin/bookings/export` endpoint + `ExcelExportService` | Done |
| T4.3 | `importExecutor` ThreadPoolTaskExecutor (core=5, max=10, CallerRunsPolicy) | Done |
| T4.4 | `TourExcelImporter` (fan-out via CompletableFuture), `TourImportJob` entity, V9 migration | Done |
| T4.5 | Admin import UI: `GET/POST /admin/tours/import`, template download | Done |
| T4.6 | Spring WS config, WSDL at `/soap/currency.wsdl` | Done |
| T4.7 | `CurrencyConversionEndpoint` @Endpoint + `CurrencyRateProvider` (VND/USD/EUR) | Done |
| T4.8 | `CurrencyConversionClient` + tour detail page shows VND/USD/EUR | Done |

---

## Day 5 — Charts, Testing & Polish

| Task | Description | Status |
|------|-------------|--------|
| T5.1 | Chart data APIs (`/admin/charts/revenue`, `/admin/charts/top-tours`) | Pending |
| T5.2 | Admin dashboard Chart.js bar charts | Pending |
| T5.3 | `BookingServiceTest` (6 Mockito unit tests) | Pending |
| T5.4 | `TourServiceTest` (5 Mockito unit tests) | Pending |
| T5.5 | `AuthControllerTest` (5 MockMvc tests) | Pending |
| T5.6 | `TourControllerTest` (5 MockMvc + security tests) | Pending |
| T5.7 | Final verification checklist | Pending |

---

## Feature Completion Matrix

| # | Feature | Target | Status |
|---|---------|--------|--------|
| 1 | ActiveMQ booking notifications | Day 2 | Done |
| 2 | RabbitMQ tour promotion broadcast | Day 2 | Done |
| 3 | WebSocket real-time push | Day 3 | Done |
| 4 | Scheduled jobs (@Scheduled + @Async) | Day 3 | Done |
| 5 | Multithreaded Excel import | Day 4 | Done |
| 6 | Apache POI Excel export | Day 4 | Done |
| 7 | SOAP currency conversion | Day 4 | Done |
| 8 | Chart.js analytics dashboard | Day 5 | Pending |
| 9 | JUnit5 + Mockito + MockMvc tests | Day 5 | Pending |
| 10 | Swagger / OpenAPI 3.0 | Day 1 | Done |
| 11 | SLF4J + Logback structured logging | Day 1 | Done |

---

## Flyway Migration Sequence

| Version | File | Applied |
|---------|------|---------|
| V1 | `init_schema.sql` | Yes |
| V2 | `seed_data.sql` | Yes |
| V3 | `seed_tours.sql` | Yes |
| V3_1 | `seed_users.sql` | Yes |
| V4 | `seed_reviews.sql` | Yes |
| V5 | `unique_payment_per_booking.sql` | Yes |
| V7 | `create_notifications_table.sql` | Yes |
| V8 | `create_scheduled_job_logs_table.sql` | Yes |
| V9 | `create_tour_import_jobs_table.sql` | Yes |
