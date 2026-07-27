# Task Advanced Breakdown — SUN Booking Tours

## Sprint Overview

| Field | Value |
|---|---|
| **Project** | SUN Booking Tours |
| **Sprint Goal** | Integrate 12 advanced technologies into existing booking system |
| **Duration** | 1 week (5 working days) |
| **Base Stack** | Spring Boot 4.0.6 · Java 21 · Spring Security 6 · JWT · OAuth2 · Thymeleaf · PostgreSQL → MySQL |
| **Dev Profile** | `application-dev.properties` → local MySQL, debug logging |
| **Prod Profile** | `application-prod.properties` → env vars, INFO logging |

---

## Day 1 (Monday) — Foundation & Infrastructure

**Theme:** Migrate database, wire up logging and API docs, verify OAuth2 providers.  
**Estimated total:** 8h

---

### Morning (4h)

#### T1.1 — PostgreSQL → MySQL Migration
- **Time:** 3h
- **Dependencies:** None
- **Files to modify:**
  - `pom.xml` — remove `postgresql` driver, add `mysql-connector-j`
  - `src/main/resources/application-dev.properties` — update datasource URL/dialect
  - `src/main/resources/application-prod.properties` — update datasource URL/dialect
  - `src/main/resources/db/migration/V1__init_schema.sql` → rewrite in MySQL syntax
  - `src/main/resources/db/migration/V2__seed_data.sql` — verify MySQL compatible
  - `src/main/resources/db/migration/V3__seed_tours.sql` — verify MySQL compatible
  - `src/main/resources/db/migration/V4__seed_reviews.sql` — verify MySQL compatible
  - `src/main/resources/db/migration/V5__unique_payment_per_booking.sql` — verify MySQL compatible
- **Key changes in DDL:**
  - `BIGSERIAL` / `SERIAL` → `BIGINT NOT NULL AUTO_INCREMENT`
  - `BOOLEAN` → `TINYINT(1)`
  - PostgreSQL `ENUM` (custom type) → inline MySQL `ENUM('A','B')`
  - `CREATE TYPE ... AS ENUM` statements → remove entirely
  - `DEFERRABLE INITIALLY DEFERRED` → remove (not supported in MySQL)
  - All tables: add `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`
  - `TEXT` → `TEXT` (compatible; use `MEDIUMTEXT` for content fields)
  - `NUMERIC(12,2)` → `DECIMAL(12,2)`
  - `NOW()` → `NOW()` (compatible)
- **pom.xml dependency:**
  ```xml
  <dependency>
      <groupId>com.mysql</groupId>
      <artifactId>mysql-connector-j</artifactId>
      <scope>runtime</scope>
  </dependency>
  ```
- **application-dev.properties:**
  ```properties
  spring.datasource.url=jdbc:mysql://localhost:3306/booking_tours?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
  spring.datasource.username=root
  spring.datasource.password=root
  spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
  spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
  spring.jpa.properties.hibernate.dialect.storage_engine=innodb
  ```
- **Acceptance criteria:**
  - `mvn spring-boot:run` starts without Flyway errors
  - All 12 tables created in MySQL with correct schema
  - Existing seed data loads correctly
  - Login and browse tours works end-to-end

---

### Afternoon (4h)

#### T1.2 — SLF4J + Logback Configuration
- **Time:** 2h
- **Dependencies:** T1.1
- **Files to create/modify:**
  - `src/main/resources/logback-spring.xml` ← **create**
  - `src/main/java/com/sunasterisk/bookingtours/filter/MdcLoggingFilter.java` ← **create**
  - `src/main/java/com/sunasterisk/bookingtours/config/LoggingConfig.java` ← **create** (register filter)
- **logback-spring.xml structure:**
  ```xml
  <!-- Console appender (dev) -->
  <!-- FILE appender: logs/app.log — INFO+, daily rolling, 30 days, 100MB -->
  <!-- FILE appender: logs/error.log — ERROR only, daily rolling, 30 days -->
  <!-- FILE appender: logs/audit.log — security events channel, INFO+ -->
  <!-- Pattern includes: %d %X{requestId} %X{userEmail} %-5level %logger{36} - %msg%n -->
  ```
- **MdcLoggingFilter:** `OncePerRequestFilter` that sets `requestId` (UUID), `userEmail` (from SecurityContext), clears on response
- **Acceptance criteria:**
  - Logs appear in `logs/app.log` at runtime
  - Each log line contains `requestId` and `userEmail` fields
  - ERROR logs also appear in `logs/error.log`
  - Log level is DEBUG in dev profile, INFO in prod profile

#### T1.3 — Swagger / OpenAPI 3.0 Setup
- **Time:** 2h
- **Dependencies:** T1.1
- **Files to create/modify:**
  - `pom.xml` — add springdoc dependency
  - `src/main/java/com/sunasterisk/bookingtours/config/SwaggerConfig.java` ← **create**
  - `src/main/resources/application-dev.properties` — enable swagger
  - All controllers in `controller/` and `controller/admin/` — add `@Tag`, `@Operation`, `@ApiResponse` annotations
- **pom.xml dependency:**
  ```xml
  <dependency>
      <groupId>org.springdoc</groupId>
      <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
      <version>2.6.0</version>
  </dependency>
  ```
- **SwaggerConfig:** Defines `OpenAPI` bean with title, description, version, security scheme (cookie JWT description)
- **SecurityConfig:** Permit `/swagger-ui/**`, `/v3/api-docs/**`
- **Acceptance criteria:**
  - `http://localhost:8080/swagger-ui.html` opens Swagger UI
  - All controllers listed with documented endpoints
  - Request/response models visible

#### T1.4 — Verify OAuth2 Facebook + Twitter
- **Time:** 1h  *(can run in parallel with T1.3)*
- **Dependencies:** T1.1
- **Files to modify:**
  - `src/main/resources/application-dev.properties` — add real Facebook & Twitter client-id/secret
- **Verification steps:**
  1. Register app on [Facebook Developers](https://developers.facebook.com) → get App ID + Secret
  2. Register app on [Twitter Developer Portal](https://developer.twitter.com) → get Client ID + Secret (OAuth 2.0)
  3. Set redirect URIs: `http://localhost:8080/login/oauth2/code/facebook`, `/twitter`
  4. Test login flow for both providers
- **Acceptance criteria:**
  - Clicking "Login with Facebook" redirects to Facebook, returns user to `/tours`
  - Clicking "Login with Twitter" redirects to Twitter, returns user to `/tours`
  - `oauth_accounts` table populated with provider + providerUserId

---

## Day 2 (Tuesday) — Messaging: ActiveMQ + RabbitMQ

**Theme:** Async messaging for booking notifications and tour promotions.  
**Estimated total:** 8h

---

### Morning (4h) — ActiveMQ

#### T2.1 — ActiveMQ Dependency & Configuration
- **Time:** 1h
- **Dependencies:** T1.1
- **Files to create/modify:**
  - `pom.xml` — add ActiveMQ dependency
  - `src/main/java/com/sunasterisk/bookingtours/config/ActiveMQConfig.java` ← **create**
  - `src/main/resources/application-dev.properties` — add broker URL
- **pom.xml dependency:**
  ```xml
  <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-activemq</artifactId>
  </dependency>
  <dependency>
      <groupId>org.apache.activemq</groupId>
      <artifactId>activemq-broker</artifactId>
  </dependency>
  ```
- **ActiveMQConfig:** Defines `ActiveMQConnectionFactory`, `JmsTemplate`, `Queue` bean named `booking.notifications`, enables JMS listener (`@EnableJms`)
- **application-dev.properties:** `spring.activemq.broker-url=vm://localhost?broker.persistent=false` (embedded broker for dev)
- **Acceptance criteria:** Application starts, embedded ActiveMQ broker log appears

#### T2.2 — Notification Entity + Flyway Migration
- **Time:** 1h
- **Dependencies:** T2.1
- **Files to create:**
  - `src/main/resources/db/migration/V6__create_notifications_table.sql` ← **create**
  - `src/main/java/com/sunasterisk/bookingtours/entity/Notification.java` ← **create**
  - `src/main/java/com/sunasterisk/bookingtours/repository/NotificationRepository.java` ← **create**
  - `src/main/java/com/sunasterisk/bookingtours/dto/NotificationDto.java` ← **create**
- **V6 SQL (MySQL):**
  ```sql
  CREATE TABLE notifications (
      id BIGINT NOT NULL AUTO_INCREMENT,
      user_id BIGINT NOT NULL,
      type ENUM('BOOKING_CONFIRMED','BOOKING_CANCELLED','PAYMENT_CONFIRMED','TOUR_PROMOTION','SYSTEM') NOT NULL,
      title VARCHAR(255) NOT NULL,
      message TEXT,
      is_read TINYINT(1) NOT NULL DEFAULT 0,
      created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
      PRIMARY KEY (id),
      INDEX idx_notifications_user_id (user_id),
      INDEX idx_notifications_is_read (user_id, is_read),
      CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
  ```
- **Notification entity:** Maps table, includes `NotificationType` enum
- **NotificationRepository:** `findByUserIdOrderByCreatedAtDesc(Long userId, Pageable)`, `countByUserIdAndIsReadFalse(Long userId)`
- **Acceptance criteria:** Flyway applies V6 cleanly; entity is usable from repository

#### T2.3 — BookingNotificationProducer (ActiveMQ)
- **Time:** 1h
- **Dependencies:** T2.1, T2.2
- **Files to create:**
  - `src/main/java/com/sunasterisk/bookingtours/messaging/activemq/BookingNotificationMessage.java` ← **create** (Serializable DTO)
  - `src/main/java/com/sunasterisk/bookingtours/messaging/activemq/BookingNotificationProducer.java` ← **create**
- **BookingNotificationProducer:** `@Component` with injected `JmsTemplate`; method `sendNotification(BookingNotificationMessage)` sends to `booking.notifications` queue
- **Acceptance criteria:** Unit test confirms message is sent to queue without error

#### T2.4 — BookingNotificationConsumer + NotificationService
- **Time:** 1h
- **Dependencies:** T2.3
- **Files to create:**
  - `src/main/java/com/sunasterisk/bookingtours/messaging/activemq/BookingNotificationConsumer.java` ← **create**
  - `src/main/java/com/sunasterisk/bookingtours/service/NotificationService.java` ← **create** (interface)
  - `src/main/java/com/sunasterisk/bookingtours/service/impl/NotificationServiceImpl.java` ← **create**
- **BookingNotificationConsumer:** `@JmsListener(destination = "booking.notifications")` → calls `NotificationService.saveNotification()`
- **NotificationService:** `saveNotification(userId, type, title, message)`, `getUnreadCount(userId)`, `getNotifications(userId, Pageable)`, `markAllRead(userId)`
- **Acceptance criteria:** Consume message → row inserted in `notifications` table

#### T2.5 — Integrate Producer into BookingService
- **Time:** 30min
- **Dependencies:** T2.4
- **Files to modify:**
  - `src/main/java/com/sunasterisk/bookingtours/service/impl/BookingServiceImpl.java`
- **Change:** After `adminConfirmBooking()` succeeds → call `producer.sendNotification(...)` with `BOOKING_CONFIRMED` type; after `adminCancelBooking()` → send `BOOKING_CANCELLED`
- **Acceptance criteria:** Admin confirms booking → notification appears in `notifications` table for that user

---

### Afternoon (4h) — RabbitMQ

#### T2.6 — RabbitMQ Dependency & Configuration
- **Time:** 1h
- **Dependencies:** T1.1
- **Files to create/modify:**
  - `pom.xml` — add AMQP starter
  - `src/main/java/com/sunasterisk/bookingtours/config/RabbitMQConfig.java` ← **create**
  - `src/main/resources/application-dev.properties` — add RabbitMQ connection
- **pom.xml dependency:**
  ```xml
  <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-amqp</artifactId>
  </dependency>
  ```
- **RabbitMQConfig:** Defines `FanoutExchange("tour.promotions")`, `Queue("tour.promo.notification.queue")`, `Queue("tour.promo.log.queue")`, two `Binding` beans, `RabbitTemplate`, `MessageConverter` (Jackson2JsonMessageConverter), enables `@EnableRabbit`
- **application-dev.properties:** `spring.rabbitmq.host=localhost`, `spring.rabbitmq.port=5672`, `spring.rabbitmq.username=guest`, `spring.rabbitmq.password=guest`
- **Acceptance criteria:** Application starts with RabbitMQ connected (requires local RabbitMQ or Docker: `docker run -d -p 5672:5672 -p 15672:15672 rabbitmq:3-management`)

#### T2.7 — TourPromotionPublisher
- **Time:** 1h
- **Dependencies:** T2.6
- **Files to create:**
  - `src/main/java/com/sunasterisk/bookingtours/messaging/rabbitmq/TourPromotionMessage.java` ← **create**
  - `src/main/java/com/sunasterisk/bookingtours/messaging/rabbitmq/TourPromotionPublisher.java` ← **create**
- **TourPromotionPublisher:** `@Component` with `RabbitTemplate`; method `publishNewTour(TourPromotionMessage)` sends to `tour.promotions` fanout exchange with empty routing key
- **Acceptance criteria:** Message is published to exchange without error

#### T2.8 — TourPromotionListeners
- **Time:** 1h
- **Dependencies:** T2.7, T2.4
- **Files to create:**
  - `src/main/java/com/sunasterisk/bookingtours/messaging/rabbitmq/TourPromotionNotificationListener.java` ← **create**
  - `src/main/java/com/sunasterisk/bookingtours/messaging/rabbitmq/TourPromotionLogListener.java` ← **create**
- **TourPromotionNotificationListener:** `@RabbitListener(queues = "tour.promo.notification.queue")` → calls `NotificationService.broadcastTourPromotion(tourId, title)` (saves TOUR_PROMOTION notification for all active users via batch insert)
- **TourPromotionLogListener:** `@RabbitListener(queues = "tour.promo.log.queue")` → logs tour promotion event with SLF4J at INFO level
- **Acceptance criteria:** New tour activated → two listeners receive message; notifications stored; log line appears

#### T2.9 — Integrate Publisher into TourService
- **Time:** 30min
- **Dependencies:** T2.8
- **Files to modify:**
  - `src/main/java/com/sunasterisk/bookingtours/service/impl/TourServiceImpl.java`
- **Change:** In `create()` and `update()` → if tour status is `ACTIVE`, call `publisher.publishNewTour(...)`
- **Acceptance criteria:** Admin creates/activates tour → RabbitMQ consumers triggered

---

## Day 3 (Wednesday) — Realtime (WebSocket/STOMP) + Scheduler

**Theme:** Real-time push notifications and automated background jobs.  
**Estimated total:** 8h

---

### Morning (4h) — WebSocket + STOMP

#### T3.1 — WebSocket Configuration
- **Time:** 1h
- **Dependencies:** T1.1
- **Files to create/modify:**
  - `pom.xml` — add WebSocket starter
  - `src/main/java/com/sunasterisk/bookingtours/config/WebSocketConfig.java` ← **create**
  - `src/main/java/com/sunasterisk/bookingtours/config/WebSocketSecurityConfig.java` ← **create**
- **pom.xml dependency:**
  ```xml
  <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-websocket</artifactId>
  </dependency>
  ```
- **WebSocketConfig:** `@EnableWebSocketMessageBroker`, configures STOMP endpoint `/ws` (SockJS fallback), simple broker for `/topic` and `/user/queue`, application destination prefix `/app`
- **WebSocketSecurityConfig:** Extends `AbstractSecurityWebSocketMessageBrokerConfigurer`, validates CSRF token for STOMP CONNECT frame
- **Acceptance criteria:** `/ws` endpoint accessible; STOMP connect succeeds from browser console

#### T3.2 — NotificationWebSocketController
- **Time:** 1h
- **Dependencies:** T3.1, T2.4
- **Files to create:**
  - `src/main/java/com/sunasterisk/bookingtours/controller/NotificationController.java` ← **create**
- **NotificationController:**
  - `@GetMapping("/api/notifications")` → return paginated notifications for current user (REST endpoint for initial load)
  - `@GetMapping("/api/notifications/unread-count")` → return `{ count: N }`
  - `@PostMapping("/api/notifications/mark-read")` → mark all as read
  - Push method: `SimpMessagingTemplate.convertAndSendToUser(email, "/queue/notifications", notifDto)` — called by `NotificationService` after saving
- **Modify NotificationServiceImpl:** Inject `SimpMessagingTemplate`; after saving notification → call `convertAndSendToUser` to push to specific user's session
- **Acceptance criteria:** Posting to `/api/notifications/unread-count` returns correct count; push is received by connected browser

#### T3.3 — Frontend: WebSocket Client Integration
- **Time:** 1h
- **Dependencies:** T3.2
- **Files to create/modify:**
  - `src/main/resources/static/js/notification.js` ← **create**
  - `src/main/resources/templates/layout/base.html` — include SockJS + STOMP scripts, include `notification.js`
- **notification.js:**
  ```javascript
  // Connect to /ws using SockJS + StompJS
  // Subscribe to /user/queue/notifications
  // On message: increment badge count, show toast (Bootstrap 5 Toast component)
  // On load: fetch /api/notifications/unread-count, set badge
  // Mark-read on bell icon click: POST /api/notifications/mark-read
  ```
- **SockJS + StompJS via CDN** (add to base.html): `https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js`, `https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js`
- **Acceptance criteria:** Browser console shows STOMP CONNECTED; receiving a notification increments badge without page reload

#### T3.4 — Notification Bell UI in Navbar
- **Time:** 1h
- **Dependencies:** T3.3
- **Files to modify:**
  - `src/main/resources/templates/layout/base.html` — add bell icon with badge to navbar
  - `src/main/resources/static/css/style.css` — badge styles
- **UI:** Bootstrap 5 `<span class="badge bg-danger" id="notif-count">` over a bell icon; hidden when count=0; clicking navigates to `/profile/notifications` or shows dropdown
- **Create `GET /profile/notifications`** in `ProfileController` → renders notifications list page
- **Create `src/main/resources/templates/profile/notifications.html`** ← **create**
- **Acceptance criteria:** Navbar bell shows correct unread count; new push notification increments badge in real-time

---

### Afternoon (4h) — Scheduler + @Async

#### T3.5 — AutoCompleteBookingJob
- **Time:** 1h
- **Dependencies:** T1.1
- **Files to create:**
  - `src/main/java/com/sunasterisk/bookingtours/scheduler/AutoCompleteBookingJob.java` ← **create**
  - `src/main/resources/db/migration/V7__create_scheduled_job_logs_table.sql` ← **create**
  - `src/main/java/com/sunasterisk/bookingtours/entity/ScheduledJobLog.java` ← **create**
  - `src/main/java/com/sunasterisk/bookingtours/repository/ScheduledJobLogRepository.java` ← **create**
- **V7 SQL:**
  ```sql
  CREATE TABLE scheduled_job_logs (
      id BIGINT NOT NULL AUTO_INCREMENT,
      job_name VARCHAR(100) NOT NULL,
      status ENUM('SUCCESS','FAILED','SKIPPED') NOT NULL,
      records_processed INT DEFAULT 0,
      duration_ms BIGINT,
      error_message TEXT,
      executed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
      PRIMARY KEY (id),
      INDEX idx_job_logs_job_name (job_name),
      INDEX idx_job_logs_executed_at (executed_at)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
  ```
- **AutoCompleteBookingJob:** `@Scheduled(cron = "0 30 0 * * *")` — query all `CONFIRMED` bookings where `tour.departureDate < LocalDate.now()` → update status to `COMPLETED` → log to `scheduled_job_logs`
- **Add to BookingRepository:** `findConfirmedBookingsPastDeparture(LocalDate today)` JPQL
- **Acceptance criteria:** Job runs at scheduled time (or on demand via test with `fixedDelay=10000`), updates bookings, logs to DB

#### T3.6 — PendingPaymentCleanupJob
- **Time:** 30min
- **Dependencies:** T3.5 (reuse ScheduledJobLog)
- **Files to create:**
  - `src/main/java/com/sunasterisk/bookingtours/scheduler/PendingPaymentCleanupJob.java` ← **create**
- **PendingPaymentCleanupJob:** `@Scheduled(cron = "0 0 1 * * *")` — find `PENDING` bookings older than 48h with no payment → cancel them → log result
- **Add to BookingRepository:** `findStalePendingBookings(LocalDateTime cutoff)` JPQL
- **Acceptance criteria:** Stale pending bookings are cancelled; job log recorded

#### T3.7 — AsyncConfig + @Async for Notifications
- **Time:** 1h
- **Dependencies:** T3.5
- **Files to create:**
  - `src/main/java/com/sunasterisk/bookingtours/config/AsyncConfig.java` ← **create**
- **AsyncConfig:** `@EnableAsync`, defines `ThreadPoolTaskExecutor` bean named `notificationExecutor` (corePoolSize=3, maxPoolSize=5, queueCapacity=100, threadNamePrefix="notif-async-")
- **Modify NotificationServiceImpl:** Add `@Async("notificationExecutor")` to `saveNotification()` and `broadcastTourPromotion()` methods
- **Acceptance criteria:** Notification save does not block HTTP response; thread name in logs is `notif-async-X`

---

## Day 4 (Thursday) — File Handling, Multithread & SOAP

**Theme:** Excel import/export with parallel processing and SOAP currency service.  
**Estimated total:** 8h

---

### Morning (4h) — Apache POI + ThreadPoolTaskExecutor

#### T4.1 — Apache POI Dependency + ExcelExportService
- **Time:** 1h
- **Dependencies:** T1.1
- **Files to create/modify:**
  - `pom.xml` — add POI dependency
  - `src/main/java/com/sunasterisk/bookingtours/excel/BookingExcelExporter.java` ← **create**
  - `src/main/java/com/sunasterisk/bookingtours/service/ExcelExportService.java` ← **create** (interface)
  - `src/main/java/com/sunasterisk/bookingtours/service/impl/ExcelExportServiceImpl.java` ← **create**
- **pom.xml dependency:**
  ```xml
  <dependency>
      <groupId>org.apache.poi</groupId>
      <artifactId>poi-ooxml</artifactId>
      <version>5.3.0</version>
  </dependency>
  ```
- **BookingExcelExporter:** Uses `XSSFWorkbook`, creates sheet "Bookings", styled header row (bold font, light blue fill, border), auto-sizes columns
- **Columns:** Booking Code, User Email, Tour Name, Participants, Total Price (VND), Status, Departure Date, Created Date
- **Acceptance criteria:** `generateBookingReport(List<Booking>)` returns `XSSFWorkbook` with correct data

#### T4.2 — Admin Export Endpoint
- **Time:** 30min
- **Dependencies:** T4.1
- **Files to modify:**
  - `src/main/java/com/sunasterisk/bookingtours/controller/admin/AdminBookingController.java`
- **Add:** `GET /admin/bookings/export` → applies same search filters as list page → calls `ExcelExportService.exportBookings(filters)` → sets response headers `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `Content-Disposition: attachment; filename=bookings-YYYY-MM-DD.xlsx` → writes workbook to response output stream
- **Acceptance criteria:** Clicking "Export Excel" in admin booking list downloads a valid .xlsx file

#### T4.3 — ThreadPoolTaskExecutor Config for Import
- **Time:** 30min
- **Dependencies:** T1.1
- **Files to modify:**
  - `src/main/java/com/sunasterisk/bookingtours/config/AsyncConfig.java`
- **Add** second `ThreadPoolTaskExecutor` bean named `importExecutor`: corePoolSize=5, maxPoolSize=10, queueCapacity=50, threadNamePrefix="tour-import-", keepAlive=60s
- **Acceptance criteria:** Bean available for injection in import service

#### T4.4 — TourExcelImporter + TourImportJob
- **Time:** 2h
- **Dependencies:** T4.3, T2.2 (reuse Flyway V8)
- **Files to create:**
  - `src/main/resources/db/migration/V8__create_tour_import_jobs_table.sql` ← **create**
  - `src/main/java/com/sunasterisk/bookingtours/entity/TourImportJob.java` ← **create**
  - `src/main/java/com/sunasterisk/bookingtours/repository/TourImportJobRepository.java` ← **create**
  - `src/main/java/com/sunasterisk/bookingtours/excel/TourExcelImporter.java` ← **create**
  - `src/main/java/com/sunasterisk/bookingtours/service/ExcelImportService.java` ← **create**
  - `src/main/java/com/sunasterisk/bookingtours/service/impl/ExcelImportServiceImpl.java` ← **create**
- **V8 SQL:**
  ```sql
  CREATE TABLE tour_import_jobs (
      id BIGINT NOT NULL AUTO_INCREMENT,
      file_name VARCHAR(255) NOT NULL,
      status ENUM('PENDING','PROCESSING','COMPLETED','FAILED') NOT NULL DEFAULT 'PENDING',
      total_rows INT DEFAULT 0,
      success_rows INT DEFAULT 0,
      failed_rows INT DEFAULT 0,
      error_details MEDIUMTEXT,
      created_by BIGINT,
      created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
      completed_at DATETIME(6),
      PRIMARY KEY (id),
      CONSTRAINT fk_import_jobs_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
  ```
- **TourExcelImporter:** Reads each data row, submits `Callable<ImportRowResult>` to `importExecutor`, collects `Future<ImportRowResult>`, aggregates success/fail
- **Excel template columns:** Title, Description, Price, Duration Days, Max Participants, Departure Location, Destination, Departure Date (yyyy-MM-dd), Category Name
- **Validation per row:** required fields, numeric ranges, valid category name, duplicate title check
- **ExcelImportServiceImpl:** Saves `TourImportJob`, calls `TourExcelImporter`, updates job status on completion
- **Acceptance criteria:** Upload 20-row Excel → job completes → valid rows inserted as INACTIVE tours → failed rows listed with reasons

#### T4.5 — Admin Import UI Endpoints
- **Time:** 30min
- **Dependencies:** T4.4
- **Files to create/modify:**
  - `src/main/java/com/sunasterisk/bookingtours/controller/admin/AdminTourController.java` — add import endpoints
  - `src/main/resources/templates/admin/tours/import.html` ← **create**
- **Add endpoints:**
  - `GET /admin/tours/import` → show upload form + download template link
  - `GET /admin/tours/import/template` → serves downloadable .xlsx template file (generated by POI)
  - `POST /admin/tours/import` → handles `MultipartFile`, runs import, redirects with result flash message
- **Acceptance criteria:** Admin can upload file and see import summary message

---

### Afternoon (4h) — SOAP Integration

#### T4.6 — Spring WS Setup & XSD Schema
- **Time:** 1h
- **Dependencies:** T1.1
- **Files to create/modify:**
  - `pom.xml` — add Spring WS and JAXB dependencies
  - `src/main/resources/wsdl/currency.xsd` ← **create**
  - `src/main/resources/wsdl/currency.wsdl` ← **create** (optional if using contract-first)
  - `src/main/java/com/sunasterisk/bookingtours/config/WebServiceConfig.java` ← **create**
- **pom.xml dependencies:**
  ```xml
  <dependency>
      <groupId>org.springframework.ws</groupId>
      <artifactId>spring-ws-core</artifactId>
  </dependency>
  <dependency>
      <groupId>wsdl4j</groupId>
      <artifactId>wsdl4j</artifactId>
  </dependency>
  ```
- **currency.xsd:** Defines `CurrencyConversionRequest` (amount DECIMAL, fromCurrency, toCurrency) and `CurrencyConversionResponse` (convertedAmount, rate, fromCurrency, toCurrency)
- **WebServiceConfig:** Extends `WsConfigurerAdapter`, registers `DefaultWsdl11Definition` at `/ws/currency`, publishes WSDL at `/ws/currency.wsdl`
- **Acceptance criteria:** `http://localhost:8080/ws/currency.wsdl` returns valid WSDL XML

#### T4.7 — CurrencyConversionEndpoint (SOAP Server)
- **Time:** 1.5h
- **Dependencies:** T4.6
- **Files to create:**
  - `src/main/java/com/sunasterisk/bookingtours/soap/CurrencyConversionEndpoint.java` ← **create**
  - `src/main/java/com/sunasterisk/bookingtours/soap/CurrencyRateProvider.java` ← **create**
- **CurrencyConversionEndpoint:** `@Endpoint`, `@PayloadRoot(namespace, localPart = "CurrencyConversionRequest")`, method annotated `@RequestPayload` / `@ResponsePayload`
- **CurrencyRateProvider:** In-memory `Map<String, BigDecimal>` of exchange rates relative to VND:
  - USD: 1 USD = 25,500 VND
  - EUR: 1 EUR = 27,800 VND
  - JPY: 1 JPY = 170 VND
  - KRW: 1 KRW = 18.5 VND
  - VND: 1 VND = 1 VND
- **Acceptance criteria:** Sending SOAP request via Postman/SoapUI returns correct converted amount

#### T4.8 — CurrencyConversionClient + Tour Page Integration
- **Time:** 1.5h
- **Dependencies:** T4.7
- **Files to create/modify:**
  - `src/main/java/com/sunasterisk/bookingtours/soap/CurrencyConversionClient.java` ← **create**
  - `src/main/java/com/sunasterisk/bookingtours/controller/TourController.java` — pass converted prices to model
  - `src/main/resources/templates/tours/detail.html` — display price in USD and EUR alongside VND
- **CurrencyConversionClient:** Extends `WebServiceGatewaySupport`, `convertPrice(BigDecimal amount, String from, String to)` method calls local SOAP endpoint
- **TourController `/tours/{id}`:** Call client to get USD and EUR equivalents of tour price → pass `priceUsd`, `priceEur` to model
- **Acceptance criteria:** Tour detail page shows price in 3 currencies (VND / USD / EUR)

---

## Day 5 (Friday) — Charts, Testing & Polish

**Theme:** Analytics charts on admin dashboard, comprehensive tests, final verification.  
**Estimated total:** 8h

---

### Morning (4h) — Chart.js Analytics

#### T5.1 — Chart Data API Endpoints
- **Time:** 2h
- **Dependencies:** T1.1
- **Files to create:**
  - `src/main/java/com/sunasterisk/bookingtours/controller/admin/AdminChartController.java` ← **create**
  - `src/main/java/com/sunasterisk/bookingtours/service/ChartService.java` ← **create**
  - `src/main/java/com/sunasterisk/bookingtours/service/impl/ChartServiceImpl.java` ← **create**
  - `src/main/java/com/sunasterisk/bookingtours/dto/ChartDataDto.java` ← **create** (labels[], datasets[])
- **Add to repositories:**
  - `BookingRepository`: `getMonthlyRevenue(LocalDateTime from, LocalDateTime to)` → `List<Object[]>` (month, revenue)
  - `BookingRepository`: `getTopToursByBookings(Pageable)` → `List<Object[]>` (tourTitle, count)
- **AdminChartController endpoints (all return `@ResponseBody` JSON):**
  - `GET /admin/charts/revenue` → last 6 months monthly revenue
  - `GET /admin/charts/top-tours` → top 5 tours by booking count
- **Acceptance criteria:** Both endpoints return JSON with `labels` and `datasets` arrays; verify in browser DevTools

#### T5.2 — Admin Dashboard Charts UI
- **Time:** 2h
- **Dependencies:** T5.1
- **Files to modify:**
  - `src/main/resources/templates/admin/dashboard.html` — add 2 chart `<canvas>` elements
  - `src/main/resources/static/js/admin-charts.js` ← **create**
  - `src/main/resources/templates/layout/base.html` — include Chart.js CDN on admin pages only
- **Chart.js CDN:** `https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js`
- **admin-charts.js:** Fetches each chart API endpoint with `fetch()`, renders:
  - Monthly Revenue → vertical bar chart (blue bars)
  - Top Tours → horizontal bar chart (green)
- **Dashboard layout:** 1×2 grid of chart cards using Bootstrap 5 grid
- **Acceptance criteria:** Admin dashboard shows 2 animated charts with real data; charts load within 2 seconds

---

### Afternoon (4h) — Testing & Final Verification

#### T5.3 — BookingServiceTest (JUnit5 + Mockito)
- **Time:** 1.5h
- **Dependencies:** None (can be done anytime after Day 1)
- **Files to create:**
  - `src/test/java/com/sunasterisk/bookingtours/service/BookingServiceTest.java` ← **create**
- **Test cases:**
  - `createBooking_success()` — mock UserRepo, TourRepo, BookingRepo; verify booking saved with correct total price
  - `createBooking_tourNotFound_throwsException()` — verify `ResourceNotFoundException` thrown
  - `cancelBooking_pendingBooking_success()` — verify status updated to CANCELLED
  - `cancelBooking_confirmedBooking_throwsException()` — verify cannot cancel confirmed booking
  - `adminConfirmBooking_success()` — verify status PENDING → CONFIRMED and ActiveMQ producer called
  - `generateBookingCode_uniqueness()` — verify format BK-YYYYMMDD-XXXX
- **Acceptance criteria:** All 6 tests pass; `mvn test -pl . -Dtest=BookingServiceTest` exits 0

#### T5.4 — TourServiceTest (JUnit5 + Mockito)
- **Time:** 1h
- **Dependencies:** None
- **Files to create:**
  - `src/test/java/com/sunasterisk/bookingtours/service/TourServiceTest.java` ← **create**
- **Test cases:**
  - `createTour_success()` — mock CategoryRepo, TourRepo; verify saved
  - `createTour_duplicateTitle_throwsException()` — verify `DuplicateResourceException`
  - `updateTour_success()` — verify fields updated
  - `deleteTour_success()` — verify `tourRepo.delete()` called
  - `getPublicById_inactiveTour_throwsException()` — verify INACTIVE tour not returned
- **Acceptance criteria:** All 5 tests pass

#### T5.5 — AuthControllerTest (MockMvc)
- **Time:** 1h
- **Dependencies:** T1.1
- **Files to create:**
  - `src/test/java/com/sunasterisk/bookingtours/controller/AuthControllerTest.java` ← **create**
- **Test cases:**
  - `showLoginPage_returns200()` — `mockMvc.perform(get("/auth/login")).andExpect(status().isOk())`
  - `register_validData_redirectsToLogin()` — POST with valid form data, expect redirect
  - `register_duplicateEmail_showsError()` — POST with existing email, expect form redisplayed with error
  - `register_passwordMismatch_showsError()` — POST with mismatched passwords, expect error
  - `login_invalidCredentials_showsError()` — POST wrong password, expect error message
- **Acceptance criteria:** All 5 tests pass; uses `@WithMockUser` where needed

#### T5.6 — TourControllerTest (MockMvc)
- **Time:** 30min
- **Dependencies:** None
- **Files to create:**
  - `src/test/java/com/sunasterisk/bookingtours/controller/TourControllerTest.java` ← **create**
- **Test cases:**
  - `listTours_public_returns200()` — unauthenticated GET `/tours` returns 200
  - `tourDetail_activeTour_returns200()` — GET `/tours/1` returns 200
  - `rateTour_unauthenticated_redirectsToLogin()` — POST `/tours/1/rate` without auth → 302 to login
  - `adminTourList_asAdmin_returns200()` — `@WithMockUser(roles="ADMIN")` GET `/admin/tours` returns 200
  - `adminTourList_asUser_returns403()` — `@WithMockUser(roles="USER")` GET `/admin/tours` returns 403
- **Acceptance criteria:** All 5 tests pass; security rules verified by tests

#### T5.7 — Final Verification Checklist
- **Time:** 1h
- **Dependencies:** All previous tasks
- **Steps:**
  1. `mvn clean test` — all tests pass, 0 failures
  2. Open `http://localhost:8080/swagger-ui.html` — all endpoints documented
  3. Login with Google OAuth2 — success
  4. Admin confirms booking → bell shows notification → WebSocket push received
  5. Admin activates tour → RabbitMQ listeners log; notifications created for users
  6. Admin export bookings → downloads valid .xlsx
  7. Admin imports tours via Excel → summary shows success/fail counts
  8. Tour detail page shows VND + USD + EUR prices (SOAP)
  9. Admin dashboard shows 2 charts with data
  10. Check `logs/app.log` — requestId and userEmail in every line
  11. Trigger scheduler manually → `scheduled_job_logs` table populated

---

## Dependencies Graph

```
T1.1 (MySQL) ──┬──> T1.2 (Logback)
               ├──> T1.3 (Swagger)
               ├──> T1.4 (OAuth2)
               ├──> T2.1 (ActiveMQ config)
               ├──> T2.6 (RabbitMQ config)
               ├──> T3.1 (WebSocket config)
               ├──> T3.5 (Scheduler)
               ├──> T4.1 (POI)
               ├──> T4.6 (Spring WS)
               └──> T5.1 (Charts)

T2.1 ──> T2.2 ──> T2.3 ──> T2.4 ──> T2.5
T2.6 ──> T2.7 ──> T2.8 ──> T2.9

T3.1 ──> T3.2 ──> T3.3 ──> T3.4
T3.5 ──> T3.6
T3.5 ──> T3.7 (AsyncConfig)

T2.2 ──> T3.7 (notificationExecutor used by NotificationService)

T4.1 ──> T4.2 ──> (export endpoint in T4.2)
T4.3 (importExecutor) ──> T4.4 ──> T4.5
T4.6 ──> T4.7 ──> T4.8

T5.1 ──> T5.2
T5.3, T5.4, T5.5, T5.6 are independent (can run in any order)
All ──> T5.7 (final verification)
```

---

## New Files Summary

### Config
| File | Purpose |
|---|---|
| `config/SwaggerConfig.java` | OpenAPI 3.0 bean definition |
| `config/ActiveMQConfig.java` | ActiveMQ ConnectionFactory, Queue, JmsTemplate |
| `config/RabbitMQConfig.java` | FanoutExchange, Queues, Bindings, RabbitTemplate |
| `config/WebSocketConfig.java` | STOMP broker, /ws endpoint |
| `config/WebSocketSecurityConfig.java` | STOMP CSRF validation |
| `config/AsyncConfig.java` | notificationExecutor + importExecutor ThreadPoolTaskExecutor beans |
| `config/WebServiceConfig.java` | Spring WS servlet, WSDL definition |

### Entity & Repository
| File | Purpose |
|---|---|
| `entity/Notification.java` | Push notification storage |
| `entity/ScheduledJobLog.java` | Scheduler run history |
| `entity/TourImportJob.java` | Excel import job tracking |
| `repository/NotificationRepository.java` | Notification queries |
| `repository/ScheduledJobLogRepository.java` | Job log queries |
| `repository/TourImportJobRepository.java` | Import job queries |

### Messaging
| File | Purpose |
|---|---|
| `messaging/activemq/BookingNotificationMessage.java` | Serializable message DTO |
| `messaging/activemq/BookingNotificationProducer.java` | JmsTemplate sender |
| `messaging/activemq/BookingNotificationConsumer.java` | @JmsListener receiver |
| `messaging/rabbitmq/TourPromotionMessage.java` | Jackson-serialized message |
| `messaging/rabbitmq/TourPromotionPublisher.java` | RabbitTemplate sender |
| `messaging/rabbitmq/TourPromotionNotificationListener.java` | Fanout consumer 1 |
| `messaging/rabbitmq/TourPromotionLogListener.java` | Fanout consumer 2 |

### Scheduler
| File | Purpose |
|---|---|
| `scheduler/AutoCompleteBookingJob.java` | Daily booking completion |
| `scheduler/PendingPaymentCleanupJob.java` | Daily pending cleanup |

### Excel / SOAP
| File | Purpose |
|---|---|
| `excel/BookingExcelExporter.java` | XSSFWorkbook booking export |
| `excel/TourExcelImporter.java` | POI row reader + parallel processing |
| `soap/CurrencyConversionEndpoint.java` | SOAP @Endpoint handler |
| `soap/CurrencyRateProvider.java` | In-memory exchange rates |
| `soap/CurrencyConversionClient.java` | WebServiceGatewaySupport client |

### Controllers & Services
| File | Purpose |
|---|---|
| `controller/NotificationController.java` | REST + STOMP push notifications |
| `controller/admin/AdminChartController.java` | Chart data JSON APIs |
| `service/NotificationService.java` + impl | Notification CRUD + push |
| `service/ExcelExportService.java` + impl | Booking Excel export |
| `service/ExcelImportService.java` + impl | Tour Excel import |
| `service/ChartService.java` + impl | Chart aggregation queries |
| `filter/MdcLoggingFilter.java` | MDC requestId/userEmail injection |

### Resources
| File | Purpose |
|---|---|
| `resources/logback-spring.xml` | Log appenders, rotation, levels |
| `resources/wsdl/currency.xsd` | SOAP request/response schema |
| `resources/db/migration/V6__create_notifications_table.sql` | |
| `resources/db/migration/V7__create_scheduled_job_logs_table.sql` | |
| `resources/db/migration/V8__create_tour_import_jobs_table.sql` | |

### Templates & Static
| File | Purpose |
|---|---|
| `templates/profile/notifications.html` | User notification list page |
| `templates/admin/tours/import.html` | Excel import form |
| `static/js/notification.js` | WebSocket client, badge update |
| `static/js/admin-charts.js` | Chart.js chart initialization |

### Tests
| File | Purpose |
|---|---|
| `test/.../service/BookingServiceTest.java` | Mockito unit tests |
| `test/.../service/TourServiceTest.java` | Mockito unit tests |
| `test/.../controller/AuthControllerTest.java` | MockMvc integration tests |
| `test/.../controller/TourControllerTest.java` | MockMvc + security tests |

---

## pom.xml New Dependencies

```xml
<!-- MySQL -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- ActiveMQ (embedded broker included) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-activemq</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.activemq</groupId>
    <artifactId>activemq-broker</artifactId>
</dependency>

<!-- RabbitMQ -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>

<!-- WebSocket + STOMP -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>

<!-- Swagger / OpenAPI -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.6.0</version>
</dependency>

<!-- Spring WS (SOAP) -->
<dependency>
    <groupId>org.springframework.ws</groupId>
    <artifactId>spring-ws-core</artifactId>
</dependency>
<dependency>
    <groupId>wsdl4j</groupId>
    <artifactId>wsdl4j</artifactId>
</dependency>

<!-- Apache POI (Excel) -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.3.0</version>
</dependency>

<!-- SLF4J + Logback: already included via spring-boot-starter -->
<!-- JUnit5 + Mockito: already included via spring-boot-starter-test -->
```

---

## Risk & Mitigation

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| MySQL dialect breaks existing JPQL queries | Medium | High | Run full app after T1.1; fix dialect-specific issues immediately |
| Facebook/Twitter OAuth2 real credentials not available | High | Medium | Use test accounts; skip E2E verification if credentials unavailable in 1h |
| RabbitMQ not installed locally | Medium | Medium | Use Docker: `docker run -d -p 5672:5672 rabbitmq:3-management` |
| SOAP JAXB code generation complexity | Medium | High | Use contract-first with hand-written XSD; generate stubs via `wsimport` or JAXB Maven plugin |
| WebSocket authentication with JWT cookie | Medium | High | Configure `WebSocketSecurityConfig` to extract JWT from cookie during STOMP CONNECT handshake |
| ThreadPoolTaskExecutor naming conflicts | Low | Low | Name each executor bean explicitly (`importExecutor`, `notificationExecutor`); use `@Qualifier` on injection |
| Apache POI memory issue on large Excel files | Low | Medium | Use `SXSSFWorkbook` (streaming) for exports over 5,000 rows |
| Test environment DB state | Medium | Medium | Use `@Transactional` + `@Rollback` on test classes; use H2 in-memory for unit tests if MySQL not available in CI |

---

## End-of-Week Testing Checklist

### Functional
- [ ] Login with email/password → JWT cookie set → redirected correctly
- [ ] Login with Google OAuth2 → success
- [ ] Login with Facebook OAuth2 → success
- [ ] Login with Twitter OAuth2 → success
- [ ] Admin confirms booking → ActiveMQ message sent → notification stored in DB
- [ ] Notification bell shows correct unread count on page load
- [ ] WebSocket push notification received in real-time after booking confirmation (no page reload)
- [ ] Admin activates tour → RabbitMQ fanout → 2 consumers process message → log + notifications created
- [ ] `AutoCompleteBookingJob` updates past CONFIRMED bookings to COMPLETED
- [ ] `PendingPaymentCleanupJob` cancels stale PENDING bookings
- [ ] Admin exports bookings → downloads valid .xlsx with correct data
- [ ] Admin imports tours via Excel → valid rows added as INACTIVE tours
- [ ] Tour detail page shows price in VND, USD, EUR (via SOAP client)
- [ ] `/ws/currency.wsdl` returns valid WSDL
- [ ] Admin dashboard shows 4 animated charts with real data
- [ ] `http://localhost:8080/swagger-ui.html` lists all endpoints

### Non-Functional
- [ ] `mvn clean test` → 0 failures, ≥20 test cases
- [ ] `logs/app.log` exists after startup with requestId in each line
- [ ] `logs/error.log` captures only ERROR level entries
- [ ] `scheduled_job_logs` table populated after scheduler runs
- [ ] All Flyway migrations V1–V8 applied cleanly to MySQL

### Security
- [ ] `/admin/**` returns 403 for USER role
- [ ] `/admin/**` returns 200 for ADMIN role
- [ ] CSRF token required on all POST forms
- [ ] JWT cookie is HttpOnly (not accessible via `document.cookie` in browser console)
- [ ] WebSocket connection rejected without valid session/JWT
