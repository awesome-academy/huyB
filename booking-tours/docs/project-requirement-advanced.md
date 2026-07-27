# SUN Booking Tours — Advanced Project Requirements

> **Version:** 2.0  
> **Sprint Duration:** 1 Week (Advanced Track)  
> **Base:** Built on top of the existing v1 implementation (Spring Boot 4.0.6, Java 21)  
> **Goal:** Extend the existing booking-tour platform with enterprise-grade features covering messaging, realtime, scheduling, multithreading, file handling, SOAP, charts, testing, API docs, and logging.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Technology Stack](#2-technology-stack)
3. [Security](#3-security)
4. [Database Migration](#4-database-migration-postgresql--mysql)
5. [Messaging — ActiveMQ & RabbitMQ](#5-messaging--activemq--rabbitmq)
6. [Realtime — WebSocket + STOMP](#6-realtime--websocket--stomp)
7. [Scheduler — @Scheduled + @Async](#7-scheduler--scheduled--async)
8. [Multithread — ThreadPoolTaskExecutor](#8-multithread--threadpooltaskexecutor)
9. [File Handling — Apache POI](#9-file-handling--apache-poi)
10. [Web Service — SOAP (Spring WS)](#10-web-service--soap-spring-ws)
11. [Charts — Chart.js / ECharts](#11-charts--chartjs--echarts)
12. [Testing — JUnit5 + Mockito + MockMvc](#12-testing--junit5--mockito--mockmvc)
13. [API Documentation — Swagger / OpenAPI](#13-api-documentation--swagger--openapi)
14. [Logging — SLF4J + Logback](#14-logging--slf4j--logback)
15. [Non-Functional Requirements](#15-non-functional-requirements)

---

## 1. Project Overview

**SUN Booking Tours Advanced** mở rộng nền tảng đặt tour du lịch hiện có với các tính năng cấp doanh nghiệp. Phiên bản này tập trung vào khả năng mở rộng, tính thời gian thực, và tích hợp nhiều hệ thống khác nhau.

### Existing Features (v1 — Preserved)
- Spring Security 6 + JWT (HttpOnly Cookie) + CSRF protection
- OAuth2 Login (Google — working; Facebook, Twitter — registered)
- Full booking/payment/review/comment/like/rating system
- Admin dashboard with basic stats
- Thymeleaf frontend + Bootstrap 5
- PostgreSQL + Flyway migrations (12 entities)

### New Features (v2 — This Sprint)
| # | Feature Area | Feature Name |
|---|---|---|
| 1 | Messaging (ActiveMQ) | Booking Status Notification Queue |
| 2 | Messaging (RabbitMQ) | Tour Promotion Broadcast |
| 3 | Realtime | Real-time Push Notifications (WebSocket) |
| 4 | Scheduler | Auto-complete & Cleanup Jobs |
| 5 | Multithread | Parallel Excel Import |
| 6 | File Handling | Excel Export/Import for Bookings & Tours |
| 7 | SOAP | Currency Conversion Web Service |
| 8 | Charts | Admin Analytics Dashboard |
| 9 | Testing | JUnit5 + Mockito + MockMvc coverage |
| 10 | API Docs | Swagger / OpenAPI 3.0 |
| 11 | Logging | SLF4J + Logback structured logging |

---

## 2. Technology Stack

### Core (Unchanged)
| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 4.0.6 |
| ORM | Spring Data JPA / Hibernate | 6.x |
| Template Engine | Thymeleaf | 3.x |
| Build Tool | Maven | 3.9+ |

### New Dependencies (v2)
| Technology | Purpose | Version |
|---|---|---|
| MySQL Connector/J | MySQL JDBC driver | 8.0+ |
| ActiveMQ Classic | Queue-based messaging | 5.18+ |
| RabbitMQ (Spring AMQP) | Event broadcast messaging | 3.x |
| Spring WebSocket + STOMP | Realtime communication | 6.x |
| Apache POI | Excel read/write | 5.3+ |
| Spring WS | SOAP Web Service | 4.x |
| Chart.js | Client-side charting | 4.x |
| SpringDoc OpenAPI | Swagger / OpenAPI 3 docs | 2.x |
| JUnit 5 | Unit & integration testing | 5.x |
| Mockito | Mocking framework | 5.x |
| SLF4J + Logback | Structured logging | 2.x |

---

## 3. Security

### 3.1 Current Status
Spring Security 6 đã được triển khai với:
- JWT authentication via HttpOnly cookies
- CSRF protection với CookieCsrfTokenRepository
- Google OAuth2 OIDC login (working) — `CustomOAuth2UserService` via `oidcUserService()`
- Facebook OAuth2 login (working) — `CustomStandardOAuth2UserService` via `userService()`; Graph API `/me?fields=id,name,email`; synthetic email fallback `facebook_{id}@noemail.local`
- Twitter OAuth2 2.0 login (working) — same `CustomStandardOAuth2UserService`; API v2 `/2/users/me`; synthetic email `twitter_{username}@noemail.local` (Twitter does not return email)
- Swagger / OpenAPI 3.0 — enabled in dev only (`springdoc.swagger-ui.enabled=true`); disabled in prod/test; JWT Cookie security scheme registered
- Login attempt throttling
- Security headers (CSP, HSTS, X-Frame-Options)

### 3.2 OAuth2 Architecture

Two separate UserService beans handle the three providers:

| Provider | Protocol | UserService | Name attribute |
|---|---|---|---|
| Google | OIDC (openid scope) | `CustomOAuth2UserService` | `email` |
| Facebook | Standard OAuth2 | `CustomStandardOAuth2UserService` | `id` |
| Twitter | Standard OAuth2 | `CustomStandardOAuth2UserService` | `synthetic_email` |

- `CustomAuthorizationRequestResolver` adds `prompt=select_account` for Google only (Facebook and Twitter do not support this parameter).
- Twitter API v2 returns user data nested under a `"data"` key; `authentication.getName()` would resolve to `Map.toString()` without the synthetic email attribute workaround.

#### Acceptance Criteria
- [ ] User có thể đăng nhập bằng Facebook account (code complete; E2E pending real credentials)
- [ ] User có thể đăng nhập bằng Twitter account (code complete; E2E pending real credentials)
- [ ] Sau đăng nhập OAuth2, redirect đúng trang (/tours hoặc /admin)
- [ ] OAuth account được lưu vào bảng `oauth_accounts`
- [ ] Không tạo duplicate user nếu email đã tồn tại
- [x] CSRF token được gửi đúng trên tất cả form POST

---

## 4. Database Migration: PostgreSQL → MySQL

### 4.1 Mục tiêu
Chuyển toàn bộ schema từ PostgreSQL sang MySQL 8.0+ trong khi đảm bảo:
- Toàn bộ 12 bảng được migrate đầy đủ
- Tất cả constraints, indexes, foreign keys được giữ nguyên
- Flyway migrations được viết lại theo MySQL syntax

### 4.2 Các điểm khác biệt cần xử lý

| PostgreSQL | MySQL 8.0 | Xử lý |
|---|---|---|
| `TEXT` | `TEXT` hoặc `LONGTEXT` | Giữ nguyên |
| `NUMERIC(12,2)` | `DECIMAL(12,2)` | Đổi sang DECIMAL |
| `BOOLEAN` | `TINYINT(1)` hoặc `BOOLEAN` | MySQL 8 hỗ trợ BOOLEAN |
| `SERIAL` / sequences | `AUTO_INCREMENT` | Thay bằng AUTO_INCREMENT |
| `ENUM` type inline | Phải define ENUM trong column | Giữ ENUM syntax |
| `NOW()` | `NOW()` | Tương thích |
| `CHECK` constraint | MySQL 8.0.16+ hỗ trợ | Giữ nguyên |
| Case-sensitive table names (Linux) | Case-insensitive by default | Dùng lowercase |
| `ON DELETE SET NULL` | Tương thích | Giữ nguyên |

### 4.3 Cấu hình kết nối MySQL

```properties
# application-dev.properties
spring.datasource.url=jdbc:mysql://localhost:3306/booking_tours?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.username=root
spring.datasource.password=root

# HikariCP
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000

# Flyway
spring.flyway.locations=classpath:db/migration/mysql
spring.flyway.baseline-on-migrate=true
```

### 4.4 Flyway Migration Files (MySQL)
```
resources/db/migration/mysql/
├── V1__init_schema.sql        (MySQL-compatible schema)
├── V2__seed_data.sql          (roles, users)
├── V3__seed_tours.sql         (tours, categories)
├── V4__seed_reviews.sql       (reviews, comments)
├── V5__unique_payment_per_booking.sql
└── V6__add_notifications.sql  (new table for v2)
```

### 4.5 Acceptance Criteria
- [ ] Application khởi động thành công với MySQL
- [ ] Tất cả Flyway migrations chạy clean không có lỗi
- [ ] CRUD operations hoạt động bình thường
- [ ] Dữ liệu seed được load đúng

---

## 5. Messaging — ActiveMQ & RabbitMQ

### 5.1 ActiveMQ — "Booking Status Notification Queue"

#### Mô tả
Khi admin xác nhận (CONFIRM) hoặc hủy (CANCEL) một booking, hệ thống gửi một message vào ActiveMQ queue. Consumer phía sau xử lý message và lưu notification vào database để user xem lại lịch sử thông báo.

#### Architecture
```
Admin Action
    │
    ▼
BookingService.confirmBooking() / cancelBooking()
    │
    ▼
BookingNotificationProducer.sendNotification(BookingNotificationMessage)
    │  [Queue: booking.notifications]
    ▼
ActiveMQ Broker (localhost:61616)
    │
    ▼
BookingNotificationConsumer.onMessage()
    │
    ▼
NotificationRepository.save(Notification)
    │
    ▼
User sees notification in /profile/notifications
```

#### Message Object
```java
public class BookingNotificationMessage implements Serializable {
    private Long bookingId;
    private String bookingCode;
    private String userEmail;
    private BookingStatus newStatus;
    private String tourName;
    private LocalDateTime timestamp;
}
```

#### Configuration
- Broker URL: `tcp://localhost:61616`
- Queue name: `booking.notifications`
- Acknowledgement mode: `CLIENT_ACKNOWLEDGE` (manual ack on successful DB save)
- Dead letter queue: `booking.notifications.DLQ` (after 3 failed retries)

#### User Story
> Với tư cách là **user**, tôi muốn nhận thông báo khi đơn đặt tour của tôi được admin xác nhận hoặc hủy, để tôi biết trạng thái mà không cần refresh trang.

#### Acceptance Criteria
- [ ] Khi admin confirm booking → message được gửi vào queue trong < 500ms
- [ ] Consumer xử lý message và lưu notification vào DB
- [ ] User xem được danh sách thông báo tại `/profile/notifications`
- [ ] Notification hiển thị: tên tour, trạng thái mới, thời gian

---

### 5.2 RabbitMQ — "Tour Promotion Broadcast"

#### Mô tả
Khi admin kích hoạt (ACTIVE) một tour mới, hệ thống broadcast event qua RabbitMQ fanout exchange. Hai consumer độc lập xử lý event: một ghi log vào DB, một tạo notification "Tour mới" cho tất cả user đang active.

#### Architecture
```
Admin activates Tour
    │
    ▼
TourPromotionPublisher.publish(TourPromotionEvent)
    │  [Exchange: tour.promotions, Type: FANOUT]
    ▼
RabbitMQ Broker (localhost:5672)
    │
    ├──► Queue: tour.promotions.log
    │        └── NotificationLogListener → logs to promotion_logs table
    │
    └──► Queue: tour.promotions.alert
             └── PromotionAlertListener → creates Notification for all active users
```

#### Event Object
```java
public class TourPromotionEvent implements Serializable {
    private Long tourId;
    private String tourTitle;
    private String destination;
    private BigDecimal price;
    private LocalDate departureDate;
    private LocalDateTime publishedAt;
}
```

#### Configuration
- Exchange: `tour.promotions` (FANOUT, durable)
- Queue 1: `tour.promotions.log` (durable)
- Queue 2: `tour.promotions.alert` (durable)
- Connection: `amqp://guest:guest@localhost:5672`

#### User Story
> Với tư cách là **user**, tôi muốn nhận thông báo khi có tour mới được kích hoạt, để tôi không bỏ lỡ các chuyến du lịch hấp dẫn.

#### Acceptance Criteria
- [ ] Khi admin set tour status = ACTIVE → event được publish lên RabbitMQ
- [ ] NotificationLogListener nhận event và ghi vào bảng `promotion_logs`
- [ ] PromotionAlertListener tạo Notification cho tất cả user active
- [ ] Hai consumer hoạt động độc lập, lỗi ở một consumer không ảnh hưởng consumer kia

---

## 6. Realtime — WebSocket + STOMP

### 6.1 Feature: "Real-time Push Notifications"

#### Mô tả
Tích hợp WebSocket + STOMP để đẩy thông báo realtime đến user khi trạng thái booking thay đổi, đồng thời cập nhật live stats trên admin dashboard.

#### WebSocket Configuration
- Endpoint kết nối: `/ws` (SockJS fallback enabled)
- Message broker prefix: `/topic` (broadcast), `/user` (private/user-specific)
- Application destination prefix: `/app`

#### Feature 1: Private Booking Notifications
- Khi booking status thay đổi → server push đến `/user/{email}/queue/notifications`
- Frontend subscribe: `/user/queue/notifications`
- Payload:
```json
{
  "title": "Booking Confirmed",
  "message": "Đơn đặt tour #BK-20250120-0042 của bạn đã được xác nhận!",
  "bookingCode": "BK-20250120-0042",
  "status": "CONFIRMED",
  "timestamp": "2025-01-20T10:30:00"
}
```

#### Feature 2: Admin Live Stats
- Khi có booking mới → broadcast đến `/topic/admin/stats`
- Admin dashboard tự cập nhật các số liệu mà không cần refresh
- Payload:
```json
{
  "todayBookingCount": 15,
  "pendingCount": 3,
  "monthlyRevenue": 45000000
}
```

#### Feature 3: Notification Bell Icon (Navbar)
- Bell icon hiển thị badge số lượng notification chưa đọc
- Subscribe WebSocket khi user đăng nhập → cập nhật badge realtime
- Click vào bell → dropdown hiển thị 5 notification gần nhất
- Click "Mark all read" → gửi POST `/api/notifications/read-all`

#### New Table: notifications
```sql
CREATE TABLE notifications (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    title       VARCHAR(255) NOT NULL,
    message     TEXT NOT NULL,
    is_read     BOOLEAN NOT NULL DEFAULT FALSE,
    type        ENUM('BOOKING', 'PROMOTION', 'SYSTEM') NOT NULL DEFAULT 'SYSTEM',
    ref_id      BIGINT,         -- booking_id hoặc tour_id tham chiếu
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_notifications_user_unread (user_id, is_read)
);
```

#### Acceptance Criteria
- [ ] User nhận notification realtime khi booking được confirm/cancel
- [ ] Admin dashboard cập nhật stats realtime khi có booking mới
- [ ] Bell icon badge cập nhật số đúng chưa đọc
- [ ] Notification vẫn hiển thị trong lịch sử nếu user offline lúc nhận

---

## 7. Scheduler — @Scheduled + @Async

### 7.1 Scheduled Jobs

#### Job 1: AutoCompleteBookingJob
- **Trigger:** `@Scheduled(cron = "0 30 0 * * *")` — Daily 00:30
- **Logic:** Tìm tất cả booking có status = CONFIRMED và `tour.departureDate < LocalDate.now()` → đổi sang COMPLETED
- **Log:** `[SCHEDULER] AutoComplete: X bookings completed`
- **Async:** Chạy bất đồng bộ bằng `@Async`

#### Job 2: PendingPaymentCleanupJob
- **Trigger:** `@Scheduled(cron = "0 0 1 * * *")` — Daily 01:00
- **Logic:** Tìm booking PENDING có `createdAt < now - 48h` và chưa có payment → đổi sang CANCELLED
- **Log:** `[SCHEDULER] Cleanup: X expired pending bookings cancelled`

### 7.2 Async Operations

Các operations được đánh dấu `@Async` để không block HTTP thread:

| Method | Reason |
|---|---|
| `NotificationService.sendAsync(notification)` | Gửi notification vào queue không block response |
| `RatingService.updateTourAvgRatingAsync(tourId)` | Tính toán aggregate không cần block |
| `ExcelService.generateReportAsync(filters)` | Export file nặng chạy nền |

#### Async Configuration
```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean("asyncExecutor")
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        return executor;
    }
}
```

#### Acceptance Criteria
- [ ] AutoCompleteBookingJob chạy đúng giờ và log kết quả
- [ ] PendingPaymentCleanupJob hủy booking hết hạn chính xác
- [ ] `@Async` methods không block HTTP response thread
- [ ] Scheduler có thể disable bằng property `scheduler.enabled=false`

---

## 8. Multithread — ThreadPoolTaskExecutor

### 8.1 Feature: "Parallel Excel Import Processing"

#### Mô tả
Khi admin upload file Excel chứa danh sách tour, mỗi dòng dữ liệu được xử lý song song bởi một thread riêng từ pool, rút ngắn thời gian import cho file lớn.

#### Thread Pool Configuration
```java
@Bean("tourImportExecutor")
public ThreadPoolTaskExecutor tourImportExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(10);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("tour-import-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
}
```

#### Processing Flow
1. Admin upload `.xlsx` file
2. Apache POI parse file → `List<TourImportRow>`
3. Chia thành batches (mỗi batch 20 rows)
4. Submit từng batch vào `tourImportExecutor` → `Future<BatchResult>`
5. Wait for all futures → aggregate kết quả
6. Trả về `ImportSummary(inserted: X, failed: Y, errors: [...])`

#### ImportSummary Response
```json
{
  "totalRows": 50,
  "inserted": 47,
  "failed": 3,
  "errors": [
    {"row": 5, "reason": "Category 'Beach' not found"},
    {"row": 12, "reason": "Price must be positive"},
    {"row": 31, "reason": "Departure date in the past"}
  ]
}
```

#### Acceptance Criteria
- [ ] File 100 dòng xử lý nhanh hơn 50% so với sequential
- [ ] Row validation lỗi không làm crash toàn bộ import
- [ ] ImportSummary hiển thị đúng số thành công / thất bại
- [ ] Thread pool không gây OOM với file lớn (max 500 rows)

---

## 9. File Handling — Apache POI

### 9.1 Excel Export: Booking List

#### Endpoint
`GET /admin/bookings/export?status={status}&from={date}&to={date}`

#### File Format (.xlsx)
| Column | Type | Format |
|---|---|---|
| Booking Code | String | BK-YYYYMMDD-XXXX |
| User Email | String | |
| Tour Name | String | |
| Participants | Integer | |
| Total Price | Number | #,##0 VND |
| Status | String | PENDING/CONFIRMED/... |
| Departure Date | Date | dd/MM/yyyy |
| Created Date | DateTime | dd/MM/yyyy HH:mm |

#### Styling Requirements
- Header row: Bold, background color `#2C3E50`, font color white
- Alternating row colors: white / `#F5F5F5`
- Auto-size all columns
- Sheet name: `Bookings_{fromDate}_{toDate}`
- Filename: `bookings_export_{timestamp}.xlsx`

### 9.2 Excel Import: Tour List

#### Template Download
`GET /admin/tours/import/template` → tải file `tour_import_template.xlsx`

#### Template Columns
| Column | Required | Validation |
|---|---|---|
| Title | Yes | Max 255 chars, unique |
| Description | No | |
| Price | Yes | > 0 |
| Duration Days | Yes | > 0, integer |
| Max Participants | Yes | > 0, integer |
| Departure Location | Yes | |
| Destination | Yes | |
| Departure Date | Yes | Future date, format yyyy-MM-dd |
| Category Name | No | Must exist in categories table |

#### Upload Endpoint
`POST /admin/tours/import` — multipart/form-data, field: `file`

#### Validation Rules
- File type: `.xlsx` only
- Max file size: 5MB
- Max rows: 500
- Skip header row (row 1)
- Validate each row independently — don't stop on first error

#### Acceptance Criteria
- [ ] Export button trên trang admin bookings → tải file .xlsx đúng format
- [ ] Styled header và alternating rows trong file export
- [ ] Import template có thể tải về
- [ ] Import xử lý song song (tích hợp với ThreadPoolTaskExecutor)
- [ ] Hiển thị import summary sau khi upload

---

## 10. Web Service — SOAP (Spring WS)

### 10.1 Feature: "Currency Conversion Web Service"

#### Mô tả
Xây dựng SOAP Web Service cung cấp chức năng quy đổi giá tour sang các đơn vị tiền tệ khác. Admin tour page gọi SOAP client để hiển thị giá tour theo nhiều loại tiền.

#### WSDL Location
`GET /ws/currency.wsdl`

#### Operations

**convertPrice**
```xml
<Request>
  <amount>5000000</amount>
  <fromCurrency>VND</fromCurrency>
  <toCurrency>USD</toCurrency>
</Request>

<Response>
  <originalAmount>5000000</originalAmount>
  <convertedAmount>196.85</convertedAmount>
  <fromCurrency>VND</fromCurrency>
  <toCurrency>USD</toCurrency>
  <exchangeRate>0.00003937</exchangeRate>
  <convertedAt>2025-01-20T10:30:00</convertedAt>
</Response>
```

**getSupportedCurrencies**
```xml
<Response>
  <currencies>VND,USD,EUR,JPY,KRW</currencies>
</Response>
```

#### Mock Exchange Rates (hardcoded)
| Currency | Rate to VND |
|---|---|
| VND | 1.0 |
| USD | 25,400 |
| EUR | 27,800 |
| JPY | 170 |
| KRW | 19 |

#### Integration
- Trang admin `/admin/tours/{id}` gọi SOAP client → hiển thị giá tour dưới dạng: `5,000,000 VND ≈ $196.85 USD ≈ €179.85 EUR`
- SOAP client tích hợp vào `CurrencyConversionClient` bean

#### Acceptance Criteria
- [ ] WSDL accessible tại `/ws/currency.wsdl`
- [ ] `convertPrice` trả về kết quả đúng với mock rates
- [ ] `getSupportedCurrencies` trả về danh sách 5 loại tiền
- [ ] Admin tour detail hiển thị giá đã quy đổi
- [ ] SOAP client xử lý lỗi khi currency không hợp lệ

---

## 11. Charts — Chart.js / ECharts

### 11.1 Admin Analytics Dashboard Enhancement

Bổ sung 4 biểu đồ vào trang `/admin` sử dụng **Chart.js 4.x**:

#### Chart 1: Monthly Revenue (Bar Chart)
- Dữ liệu: Revenue của 6 tháng gần nhất từ CONFIRMED bookings
- API: `GET /admin/charts/revenue?months=6`
- Response: `[{month: "Jan 2025", revenue: 45000000}, ...]`

#### Chart 2: Top 5 Tours by Booking Count (Horizontal Bar)
- Dữ liệu: 5 tour được đặt nhiều nhất
- API: `GET /admin/charts/top-tours`
- Response: `[{tourName: "Đà Lạt 3N2Đ", bookingCount: 15}, ...]`

#### Chart Data APIs
Tất cả chart APIs:
- Method: `GET`
- Response format: `application/json`
- Security: yêu cầu ROLE_ADMIN
- Cache: 5 phút (Spring Cache hoặc đơn giản hơn là query thẳng)

#### Layout
- 2 charts hiển thị dạng 1x2 grid dưới stats cards hiện có
- Responsive: 1 column trên mobile, 2 columns trên tablet/desktop
- Màu sắc nhất quán với Bootstrap theme hiện tại

#### Acceptance Criteria
- [ ] 2 charts render đúng trên trang admin dashboard
- [ ] Data fetch từ API endpoint (không hardcode)
- [ ] Charts responsive trên mobile
- [ ] Chart APIs yêu cầu ROLE_ADMIN

---

## 12. Testing — JUnit5 + Mockito + MockMvc

### 12.1 Unit Tests (Service Layer)

**BookingServiceTest**
- `createBooking_success` — booking được tạo với code đúng format
- `createBooking_tourNotFound` — throw TourNotFoundException
- `createBooking_tourInactive` — throw TourNotActiveException
- `cancelBooking_success` — PENDING → CANCELLED
- `cancelBooking_notPending` — throw InvalidBookingStatusException
- `adminConfirmBooking_success` — PENDING → CONFIRMED

**TourServiceTest**
- `create_success` — tour được tạo và lưu
- `create_duplicateTitle` — throw DuplicateTitleException
- `getPublicById_inactive` — throw TourNotFoundException
- `update_success` — fields được cập nhật

**ReviewServiceTest**
- `create_success` — review được tạo với PUBLISHED status
- `delete_notOwner` — throw AccessDeniedException
- `hideReview_admin` — status đổi sang HIDDEN

**RatingServiceTest**
- `rate_newRating` — rating được tạo mới
- `rate_updateExisting` — rating cũ được cập nhật
- `rate_invalidScore` — throw ValidationException

### 12.2 Integration Tests (MockMvc)

**AuthControllerTest**
- `GET /auth/login` → 200 OK, trả về login page
- `POST /auth/login` với credentials hợp lệ → redirect, JWT cookie set
- `POST /auth/login` với sai password → 200 OK, hiển thị lỗi
- `POST /auth/register` với email trùng → redirect with error

**TourControllerTest**
- `GET /tours` → 200 OK, public access
- `GET /tours/{id}` active tour → 200 OK
- `GET /tours/{id}` inactive tour → 404
- `POST /tours/{id}/rate` khi chưa login → redirect to login

**BookingControllerTest**
- `GET /bookings` chưa login → redirect /auth/login
- `POST /bookings` với tour hợp lệ → redirect to confirmation
- `POST /bookings/{id}/cancel` → success, status = CANCELLED

### 12.3 Security Tests

- `/admin/**` với ROLE_USER → 403 Forbidden
- `/admin/**` không login → redirect /auth/login
- CSRF token missing trên POST → 403 Forbidden
- Admin endpoints với admin credentials → 200 OK

### 12.4 Coverage Target
- Service layer: ≥ 60% line coverage
- Controller layer: key endpoints covered

#### Acceptance Criteria
- [ ] Tất cả unit tests pass `mvn test`
- [ ] MockMvc integration tests pass
- [ ] Security access control tests pass
- [ ] No flaky tests (tests không phụ thuộc vào thứ tự chạy)

---

## 13. API Documentation — Swagger / OpenAPI

### 13.1 Setup

**Dependency:** `springdoc-openapi-starter-webmvc-ui 2.6.0`

**Access URL (dev only):** `http://localhost:8080/swagger-ui.html`

**OpenAPI JSON:** `http://localhost:8080/v3/api-docs`

**Security scheme:** `JWT Cookie` (APIKEY in cookie `JWT_TOKEN`) — defined in `SwaggerConfig.java`.

**Production:** Swagger UI and API docs are disabled (`springdoc.swagger-ui.enabled=false`, `springdoc.api-docs.enabled=false` in `application-prod.properties`).

### 13.2 API Groups

**Public APIs** — không cần auth
- `GET /tours` — Tour listing
- `GET /tours/{id}` — Tour detail
- `GET /reviews` — Review listing
- `GET /reviews/{id}` — Review detail

**User APIs** — yêu cầu đăng nhập (JWT cookie)
- `POST /bookings` — Create booking
- `GET /bookings` — My bookings
- `POST /reviews` — Create review
- `POST /reviews/{reviewId}/like` — Like review

**Admin APIs** — yêu cầu ROLE_ADMIN
- `GET /admin/bookings` — All bookings
- `POST /admin/bookings/{id}/confirm` — Confirm booking
- `GET /admin/bookings/export` — Export Excel
- `POST /admin/tours/import` — Import tours

**Chart APIs** — yêu cầu ROLE_ADMIN
- `GET /admin/charts/revenue`
- `GET /admin/charts/top-tours`

### 13.3 Documentation Requirements
- Mỗi endpoint có: summary, description, request/response examples
- Models có field descriptions và validation constraints
- Nhóm endpoints theo tags: `Auth`, `Tours`, `Bookings`, `Reviews`, `Admin`, `Charts`
- Mô tả authentication scheme (JWT cookie + CSRF header)

#### Acceptance Criteria
- [x] Swagger UI accessible và hiển thị đầy đủ endpoints (14 controllers annotated with `@Tag` + `@Operation`)
- [ ] Request/response examples đúng với thực tế
- [x] Swagger UI không accessible trong môi trường production

---

## 14. Logging — SLF4J + Logback

### 14.1 Log Files Structure
```
logs/
├── app.log          (INFO và trên — general application logs)
├── error.log        (ERROR only — exceptions và lỗi nghiêm trọng)
└── audit.log        (security events — login, logout, failed attempts)
```

### 14.2 Log Rotation
- Rotate theo ngày: `app.%d{yyyy-MM-dd}.log`
- Giữ tối đa 30 ngày
- Max size mỗi file: 100MB
- Compress file cũ: `.gz`

### 14.3 MDC Fields
Mọi HTTP request đều được inject MDC (Mapped Diagnostic Context):
- `requestId` — UUID được sinh tự động cho mỗi request
- `userEmail` — email của user đang đăng nhập (nếu có)
- `httpMethod` — GET, POST, PUT, DELETE
- `requestPath` — /tours, /bookings, ...

### 14.4 Log Patterns

**Console (dev):**
```
[%d{HH:mm:ss}] [%thread] %-5level [%X{requestId}] %logger{36} - %msg%n
```

**File (prod):**
```json
{"timestamp":"%d{ISO8601}","level":"%-5level","requestId":"%X{requestId}","user":"%X{userEmail}","class":"%logger{36}","message":"%msg"}%n
```

### 14.5 Mandatory Log Points

| Event | Level | Log Content |
|---|---|---|
| HTTP request received | INFO | method, path, user |
| HTTP response sent | INFO | path, status, duration ms |
| User login success | INFO (audit.log) | email, IP, provider |
| User login failed | WARN (audit.log) | email, IP, reason |
| Scheduler job start | INFO | job name, trigger time |
| Scheduler job finish | INFO | job name, duration, records processed |
| Message sent to queue | DEBUG | queue/exchange name, payload summary |
| Message consumed | DEBUG | consumer name, message id |
| Exception thrown | ERROR | exception class, message, stack trace |
| Booking status change | INFO | bookingId, oldStatus → newStatus |
| Excel import/export | INFO | filename, row count, duration |

### 14.6 Log Levels by Profile
| Logger | DEV | PROD |
|---|---|---|
| `com.sunasterisk` | DEBUG | INFO |
| `org.springframework.security` | DEBUG | WARN |
| `org.hibernate.SQL` | DEBUG | OFF |
| `org.springframework.web` | DEBUG | WARN |
| Root | INFO | WARN |

#### Acceptance Criteria
- [ ] 3 log files được tạo tại `logs/` khi app khởi động
- [ ] requestId xuất hiện trong mọi log line của cùng một request
- [ ] Login/logout được ghi vào `audit.log`
- [ ] Log rotation hoạt động đúng (có thể test với max-size nhỏ)
- [ ] Không có sensitive data (password, JWT token) trong log

---

## 15. Non-Functional Requirements

### 15.1 Performance
- Page load time < 2 giây (trừ trang chart dashboard)
- Excel export 1000 dòng < 5 giây
- Excel import 100 dòng < 10 giây
- WebSocket notification delay < 1 giây

### 15.2 Reliability
- Scheduler jobs có retry logic (tối thiểu 1 retry khi fail)
- Message consumers có error handling và dead-letter queue
- Async operations không làm crash main thread khi lỗi

### 15.3 Security
- SOAP endpoint không expose sensitive data
- Chart APIs yêu cầu authentication
- Excel import validate đầu vào để chống macro injection
- Swagger UI bị ẩn trên production (`springdoc.api-docs.enabled=false` với profile prod)

### 15.4 Maintainability
- Mỗi feature có ít nhất 1 unit test
- Log message rõ ràng, có context đủ để debug
- Configuration qua `application.properties` không hardcode

### 15.5 Development Environment Requirements
| Service | Default Port |
|---|---|
| MySQL | 3306 |
| ActiveMQ | 61616 (broker), 8161 (web console) |
| RabbitMQ | 5672 (broker), 15672 (management UI) |
| Application | 8080 |

---

*Document cuối cùng được review và approve bởi team lead trước khi bắt đầu sprint.*
