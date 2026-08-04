# System Architecture — SUN Booking Tours

**Version:** 2.5 (post T5)  
**Stack:** Spring Boot 4.0.6 · Java 21 · MySQL 8 · Thymeleaf · Bootstrap 5  
**Last updated:** 2026-08-04

---

## High-Level Component Map

```
Browser (Thymeleaf + JS)
    │
    ├── HTTP/HTTPS ──────────────────────► Spring MVC Controllers
    │                                         │
    ├── WebSocket (SockJS/STOMP) ────────►  WebSocketConfig (/ws)
    │                                         │
    └── SOAP HTTP ──────────────────────►  Spring WS (/soap/*)
                                              │
                          ┌───────────────────┼────────────────────┐
                          │                   │                    │
                     Service Layer      Messaging Layer      Scheduler Layer
                          │                   │                    │
                     JPA / Hibernate    ActiveMQ (JMS)     @Scheduled Jobs
                          │             RabbitMQ (AMQP)           │
                       MySQL 8          SimpMessaging       ScheduledJobLog
```

---

## Package Structure

```
com.sunasterisk.bookingtours/
├── config/               # Spring configuration beans
│   ├── SecurityConfig.java
│   ├── WebSocketConfig.java
│   ├── WebSocketSecurityConfig.java
│   ├── ActiveMQConfig.java
│   ├── RabbitMQConfig.java
│   ├── AsyncConfig.java          # notificationExecutor + importExecutor
│   ├── WebServiceConfig.java     # Spring WS servlet + WSDL
│   ├── SwaggerConfig.java
│   └── CustomAuthorizationRequestResolver.java
├── controller/
│   ├── TourController.java       # /tours — calls SOAP client for currency
│   ├── NotificationController.java
│   └── admin/
│       ├── AdminBookingController.java  # includes /export endpoint
│       ├── AdminTourController.java     # includes /import endpoints
│       └── AdminChartController.java
├── entity/               # JPA entities
│   ├── Notification.java
│   ├── ScheduledJobLog.java
│   └── TourImportJob.java
├── excel/                # Apache POI layer
│   ├── annotation/
│   │   └── ExcelColumn.java          # Maps DTO fields → column index + label
│   ├── dto/
│   │   ├── BookingExcelRow.java      # Export DTO
│   │   └── TourExcelRow.java         # Import DTO
│   ├── ExcelMapper.java              # Generic reflection mapper (@Component)
│   ├── ExcelValueCodec.java          # Encode/decode String ↔ typed field value
│   ├── BookingExcelExporter.java     # Delegates row/cell logic to ExcelMapper
│   └── TourExcelImporter.java        # Uses mapper.importRow(); COLUMN_COUNT reflection-derived
├── messaging/
│   ├── activemq/
│   │   ├── BookingNotificationMessage.java
│   │   ├── BookingNotificationProducer.java
│   │   └── BookingNotificationConsumer.java
│   └── rabbitmq/
│       ├── TourPromotionMessage.java
│       ├── TourPromotionPublisher.java
│       ├── TourPromotionNotificationListener.java
│       └── TourPromotionLogListener.java
├── scheduler/
│   ├── AutoCompleteBookingJob.java
│   └── PendingPaymentCleanupJob.java
├── service/
│   ├── ExcelExportService.java / impl/ExcelExportServiceImpl.java
│   ├── ExcelImportService.java  / impl/ExcelImportServiceImpl.java
│   ├── NotificationService.java / impl/NotificationServiceImpl.java
│   └── ChartService.java        / impl/ChartServiceImpl.java
├── soap/
│   ├── CurrencyConversionRequest.java   # Manual JAXB (no codegen)
│   ├── CurrencyConversionResponse.java
│   ├── CurrencyConversionEndpoint.java  # @Endpoint
│   ├── CurrencyConversionClient.java    # WebServiceGatewaySupport
│   └── CurrencyRateProvider.java
└── filter/
    └── MdcLoggingFilter.java
```

---

## Layer Details

### Web / Controller Layer

| Route | Controller | Notes |
|-------|-----------|-------|
| `GET /admin/bookings/export` | `AdminBookingController` | Streams `.xlsx`; same filters as list page |
| `GET /admin/tours/import` | `AdminTourController` | Shows upload form + recent jobs |
| `GET /admin/tours/import/template` | `AdminTourController` | POI-generated template download |
| `POST /admin/tours/import` | `AdminTourController` | `MultipartFile`; redirects with flash |
| `GET /tours/{id}` | `TourController` | Calls SOAP client; adds `priceUsd`, `priceEur` to model |
| `/soap/*` | Spring WS `MessageDispatcherServlet` | WSDL at `/soap/currency.wsdl` |

### Excel Layer (`excel/`)

**Export flow:**
```
AdminBookingController
  └── ExcelExportService.exportBookings(filters)
        └── BookingService.search(filters, Pageable.unpaged())
              └── BookingExcelExporter.generate(bookings)  → XSSFWorkbook
                    └── ExcelMapper.exportRow(BookingExcelRow, Row, CellStyle)
                          └── @ExcelColumn reflection → cell writes
                    → HttpServletResponse output stream
```

**Import flow:**
```
AdminTourController (POST /admin/tours/import)
  └── ExcelImportService.importTours(MultipartFile, adminUser)
        ├── Creates TourImportJob (PENDING → PROCESSING)
        ├── TourExcelImporter.parse(file)
        │     ├── POI reads rows → String[][] on calling thread
        │     └── CompletableFuture.supplyAsync(row → ExcelMapper.importRow(String[], TourExcelRow.class), importExecutor)
        │           × N rows in parallel
        └── Aggregates results → persist valid rows → update job (COMPLETED/FAILED)
```

### SOAP Layer (`soap/`)

**Server side:**
```
/soap/* (MessageDispatcherServlet)
  └── CurrencyConversionEndpoint (@Endpoint)
        └── CurrencyRateProvider (in-memory VND base rates)
              Rates: USD=25500, EUR=27800, JPY=170, KRW=18.5, VND=1
```

**Client side:**
```
TourController.tourDetail()
  └── CurrencyConversionClient.convertPrice(amount, "VND", "USD")
  └── CurrencyConversionClient.convertPrice(amount, "VND", "EUR")
        → WebServiceGatewaySupport → HTTP to /soap
```

WSDL endpoint: `GET /soap/currency.wsdl`

### Messaging Layer

**ActiveMQ (JMS):**
```
BookingServiceImpl (confirm/cancel)
  └── BookingNotificationProducer → queue: booking.notifications
        └── BookingNotificationConsumer (@JmsListener)
              └── NotificationService.saveNotification() [@Async notificationExecutor]
                    └── SimpMessagingTemplate.convertAndSendToUser()  (WebSocket push)
```

**RabbitMQ (AMQP):**
```
TourServiceImpl (set ACTIVE)
  └── TourPromotionPublisher → exchange: tour.promotions (FANOUT)
        ├── tour.promo.log.queue → TourPromotionLogListener (SLF4J INFO)
        └── tour.promo.notification.queue → TourPromotionNotificationListener
              └── NotificationService.broadcastTourPromotion() [@Async notificationExecutor]
```

### Scheduler Layer

| Job | Cron | Action |
|-----|------|--------|
| `AutoCompleteBookingJob` | `0 30 0 * * *` | CONFIRMED bookings past departure → COMPLETED |
| `PendingPaymentCleanupJob` | `0 0 1 * * *` | PENDING bookings > 48 h, no payment → CANCELLED |

Both jobs write a row to `scheduled_job_logs` on completion.

### Thread Pools (AsyncConfig)

| Bean | Prefix | Core | Max | Queue | Policy |
|------|--------|------|-----|-------|--------|
| `notificationExecutor` | `notif-async-` | 3 | 5 | 100 | default |
| `importExecutor` | `tour-import-` | 5 | 10 | 50 | `CallerRunsPolicy` |

### Security

- Spring Security 6, JWT via HttpOnly cookie, CSRF (`CookieCsrfTokenRepository`)
- OAuth2: Google (OIDC via `CustomOAuth2UserService`), Facebook + Twitter (Standard OAuth2 via `CustomStandardOAuth2UserService`)
- WebSocket STOMP frames validated by `WebSocketSecurityConfig`
- SOAP endpoint does not require auth (currency rates are public/mock)
- Swagger UI enabled in `dev` profile only; disabled in `prod` / `test`

---

## Database Schema (MySQL 8, InnoDB)

### Core tables (v1, V1–V6)
`users`, `roles`, `user_roles`, `tours`, `tour_categories`, `bookings`, `payments`, `reviews`, `comments`, `likes`, `ratings`, `oauth_accounts`

### v2 additions

| Table | Migration | Purpose |
|-------|-----------|---------|
| `notifications` | V7 | Push notification storage; indexed on `(user_id, is_read)` |
| `scheduled_job_logs` | V8 | Scheduler run history; indexed on `job_name`, `executed_at` |
| `tour_import_jobs` | V9 | Excel import job tracking; FK to `users(id)` ON DELETE SET NULL |

### tour_import_jobs columns
`id`, `file_name`, `status` (ENUM PENDING/PROCESSING/COMPLETED/FAILED), `total_rows`, `success_rows`, `failed_rows`, `error_details` (MEDIUMTEXT), `created_by` (FK users), `created_at`, `completed_at`

---

## External Services & Ports

| Service | Default Port | Usage |
|---------|-------------|-------|
| MySQL 8 | 3306 | Primary datastore |
| ActiveMQ | 61616 (broker), 8161 (console) | Booking notification queue |
| RabbitMQ | 5672 (broker), 15672 (mgmt UI) | Tour promotion fanout |
| Application | 8080 | Spring Boot embedded Tomcat |

---

## Logging

Three log files under `logs/`:

| File | Level filter | Pattern |
|------|-------------|---------|
| `app.log` | INFO+ | JSON with `requestId`, `userEmail`, class, message |
| `error.log` | ERROR only | same |
| `audit.log` | INFO+ (security channel) | login/logout/failure events |

`MdcLoggingFilter` (`OncePerRequestFilter`) injects `requestId` (UUID) and `userEmail` from `SecurityContext` into MDC at the start of every request; clears on response.

Rotation: daily, 30-day retention, 100 MB max per file, `.gz` compressed.
