# Demo Scenarios — Day 1 → Day 4

> **Project:** SUN Booking Tours · Spring Boot 4 · Java 21  
> **Mục đích:** Kịch bản demo toàn bộ 12 advanced features + giải thích cách implement từng feature.

---

## Chuẩn bị môi trường

```bash
# 1. MySQL đang chạy, tạo database
mysql -u root -p'Aa@123456' -e "CREATE DATABASE IF NOT EXISTS booking_tours CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. RabbitMQ (Docker)
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management

# 3. Khởi động app
cd /Users/nguyen.duc.huyb/IdeaProjects/huyB/booking-tours
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

App sẵn sàng khi log in: `Started BookingToursApplication in X.XXX seconds`

---

## Day 1 — Foundation & Infrastructure

### Feature 1: PostgreSQL → MySQL Migration

#### Kịch bản demo

```bash
# Mở MySQL, xác nhận 9 bảng đã được tạo bởi Flyway
mysql -u root -p'Aa@123456' booking_tours -e "SHOW TABLES;"

# Xem kiểu cột MySQL (không còn BIGSERIAL, BOOLEAN của PostgreSQL)
mysql -u root -p'Aa@123456' booking_tours -e "DESCRIBE users;"
# → id: bigint NOT NULL AUTO_INCREMENT  (thay vì BIGSERIAL)
# → is_active: tinyint(1)               (thay vì BOOLEAN)

# Xem ENUM inline của MySQL
mysql -u root -p'Aa@123456' booking_tours -e "SHOW CREATE TABLE bookings\G"
# → status enum('PENDING','CONFIRMED','CANCELLED','COMPLETED')

# Đăng nhập bình thường vẫn chạy
curl -s http://localhost:8080/tours | grep -o "<title>.*</title>"
```

**Điểm kiểm tra:** Flyway log `Successfully applied N migrations`, app khởi động không lỗi, browse tour được.

#### Cách implement

Migration từ PostgreSQL sang MySQL là một **DDL translation** theo từng rule:

| PostgreSQL | MySQL | Lý do |
|---|---|---|
| `BIGSERIAL` | `BIGINT NOT NULL AUTO_INCREMENT` | MySQL không có SERIAL type |
| `BOOLEAN` | `TINYINT(1)` | MySQL biểu diễn boolean bằng số |
| `CREATE TYPE ... AS ENUM` | Bỏ, dùng `ENUM(...)` inline | MySQL không hỗ trợ custom type |
| `DEFERRABLE INITIALLY DEFERRED` | Bỏ | MySQL không hỗ trợ deferred constraint |
| `NUMERIC(12,2)` | `DECIMAL(12,2)` | Tên khác nhau, cú pháp tương đương |

Sau đó thay driver trong `pom.xml` (`mysql-connector-j`) và cập nhật `application-dev.properties` với JDBC URL, dialect MySQL. Flyway tự động chạy migration khi app khởi động.

---

### Feature 2: SLF4J + Logback Structured Logging

#### Kịch bản demo

```bash
# Gửi request bất kỳ
curl http://localhost:8080/tours

# Xem log file — mỗi dòng có requestId và userEmail
tail -20 logs/app.log
# → 2026-08-04 11:00:01 [abc-123-uuid] [anonymous] INFO  c.s.b.controller.TourController - Fetching tours

# Error log riêng biệt
tail -5 logs/error.log

# Debug vs INFO: dev profile log nhiều hơn prod
grep "DEBUG" logs/app.log | head -3
```

#### Cách implement

Có 2 thành phần chính:

**1. `logback-spring.xml`** — cấu hình 3 appender:
- `CONSOLE`: output ra terminal (dev)
- `FILE_APP`: ghi `logs/app.log`, INFO+, rolling daily, giữ 30 ngày
- `FILE_ERROR`: ghi `logs/error.log`, chỉ ERROR level

Pattern log: `%d %X{requestId} %X{userEmail} %-5level %logger{36} - %msg%n`  
Phần `%X{...}` đọc từ **MDC (Mapped Diagnostic Context)** — một thread-local map.

**2. `MdcLoggingFilter`** — `OncePerRequestFilter` chạy trước mọi request:
```
request vào → set MDC["requestId"] = UUID.randomUUID()
              set MDC["userEmail"] = SecurityContextHolder user
request ra  → MDC.clear()   ← tránh memory leak
```

Profile-aware: `logback-spring.xml` dùng `<springProfile name="dev">` để bật DEBUG, prod mặc định INFO.

---

### Feature 3: Swagger / OpenAPI 3.0

> **RestController được dùng để demo:** `NotificationController` (`GET /api/notifications/unread-count`, `GET /api/notifications`, `POST /api/notifications/mark-read`)

#### Bước 1 — Lấy JWT Token từ debug log

Sau khi login (email/password hoặc OAuth2), token được in ra `logs/app.log`:

```bash
# Login xong, grep ngay để lấy token
grep "JWT_TOKEN generated" logs/app.log | tail -1
# → DEBUG [...] JWT_TOKEN generated for [user@example.com]: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ...

# Gán vào biến shell để dùng tiếp
JWT_TOKEN=$(grep "JWT_TOKEN generated" logs/app.log | tail -1 | awk -F': ' '{print $NF}')
echo $JWT_TOKEN
```

#### Bước 2 — Khám phá Swagger UI

```
Mở trình duyệt: http://localhost:8080/swagger-ui.html

1. Thấy các controller group: Auth | Notifications | Tours | Bookings | Admin/...
2. Mở nhóm "Notifications" → thấy 3 endpoints có @Operation summary:
   - GET  /api/notifications            "Lấy danh sách thông báo có phân trang"
   - GET  /api/notifications/unread-count  "Số thông báo chưa đọc"
   - POST /api/notifications/mark-read  "Đánh dấu tất cả thông báo đã đọc"
3. Mở http://localhost:8080/v3/api-docs → JSON schema toàn bộ API
```

#### Bước 3 — Gọi RestController qua curl với JWT

```bash
# 3a. Số thông báo chưa đọc
curl -s http://localhost:8080/api/notifications/unread-count \
  --cookie "jwt_token=$JWT_TOKEN" | python3 -m json.tool
# → { "count": 2 }

# 3b. Danh sách notifications (phân trang)
curl -s "http://localhost:8080/api/notifications?page=0&size=5" \
  --cookie "jwt_token=$JWT_TOKEN" | python3 -m json.tool
# → { "content": [...], "totalElements": 2, "totalPages": 1 }

# 3c. Đánh dấu tất cả đã đọc (POST)
curl -s -X POST http://localhost:8080/api/notifications/mark-read \
  --cookie "jwt_token=$JWT_TOKEN" \
  -H "X-XSRF-TOKEN: <csrf_token>" \
  -w "\nHTTP %{http_code}"
# → HTTP 200

# 3d. Gọi lại unread-count → xác nhận về 0
curl -s http://localhost:8080/api/notifications/unread-count \
  --cookie "jwt_token=$JWT_TOKEN" | python3 -m json.tool
# → { "count": 0 }
```

> **Lưu ý cookie name:** Tên cookie mặc định là `jwt_token` (cấu hình trong `app.jwt.cookie-name`). Xác nhận bằng:
> ```bash
> grep "jwt.cookie-name" src/main/resources/application-dev.properties
> ```

#### Bước 4 — Gọi trực tiếp từ Swagger UI (browser đã login)

```
1. Đăng nhập tại http://localhost:8080/auth/login (browser tự nhận cookie)
2. Mở http://localhost:8080/swagger-ui.html (cùng origin → cookie được gửi tự động)
3. Mở "Notifications" → GET /api/notifications/unread-count
4. Click "Try it out" → "Execute"
5. Thấy response 200 + JSON { "count": N } — không cần paste token thủ công
```

#### Cách implement

Thêm dependency `springdoc-openapi-starter-webmvc-ui` vào `pom.xml`.

`SwaggerConfig.java` tạo bean `OpenAPI` với:
- Thông tin project (title, description, version)
- Security scheme: mô tả JWT cookie (không enforce, chỉ document)

`NotificationController` là `@RestController` duy nhất trong project, được annotate đầy đủ:
```java
@Tag(name = "Notifications", description = "Quản lý thông báo người dùng")
@RestController
@RequestMapping("/api/notifications")
// ...
@Operation(summary = "Số thông báo chưa đọc")
@GetMapping("/unread-count")
public ResponseEntity<Map<String, Long>> getUnreadCount(...) { ... }
```

`SecurityConfig` mở public access cho `/swagger-ui/**` và `/v3/api-docs/**`. Prod profile disable Swagger qua `springdoc.api-docs.enabled=false`.

---

### Feature 4: OAuth2 — Facebook + Twitter

#### Kịch bản demo

```
1. Mở http://localhost:8080/auth/login
2. Click "Login with Facebook" → redirect sang Facebook auth
3. Cho phép app → callback về /login/oauth2/code/facebook
4. App tạo user mới (nếu chưa có) và set JWT cookie
5. Redirect về trang chủ — đã đăng nhập

[Riêng Twitter — không có email]
6. Login với Twitter → Twitter không trả về email
7. App dùng email synthetic: twitter_{username}@noemail.local
8. JWT token dùng synthetic email này làm principal
```

#### Cách implement

Spring Security OAuth2 Client hỗ trợ Google/GitHub sẵn. Facebook và Twitter cần custom vì:

**Facebook:** API Graph trả về `id`, `name`, `email` từ endpoint `/me?fields=id,name,email`.  
→ Implement `CustomStandardOAuth2UserService extends DefaultOAuth2UserService`, override `loadUser()` để map attributes về `UserPrincipal`.

**Twitter:** API v2 trả về response dạng `{ "data": { "id": ..., "name": ..., "username": ... } }` — không có email.  
→ Unwrap key `"data"` thủ công.  
→ Tạo synthetic email: `twitter_{username}@noemail.local` để Spring Security có `getName()` hợp lệ.

`SecurityConfig` wire 2 service:
```java
.userInfoEndpoint(u -> u
    .oidcUserService(customOAuth2UserService)          // Google (OIDC)
    .userService(customStandardOAuth2UserService)      // Facebook, Twitter
)
```

`CustomAuthorizationRequestResolver` thêm `prompt=select_account` chỉ cho Google, không áp dụng cho Facebook/Twitter.

---

## Day 2 — Messaging: ActiveMQ + RabbitMQ

### Feature 5: ActiveMQ — Booking Notification Queue

#### Kịch bản demo

```
1. Đăng nhập user thường → đặt tour → booking ở trạng thái PENDING

2. Đăng nhập admin → vào /admin/bookings
3. Click "Xác nhận" booking đó

4. Kiểm tra DB:
```
```sql
SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT 1;
-- → type: BOOKING_CONFIRMED, title: "Đặt tour đã được xác nhận"
```
```
5. Trong log:
grep "BookingNotificationConsumer" logs/app.log
# → [notif-async-1] Saved notification for user 5: BOOKING_CONFIRMED
```

#### Cách implement

Luồng **point-to-point queue** với ActiveMQ:

```
AdminService.confirmBooking()
    → BookingNotificationProducer.sendNotification(msg)
        → JmsTemplate.send("booking.notifications", msg)
            → [ActiveMQ queue]
                → BookingNotificationConsumer @JmsListener
                    → NotificationService.saveNotification()
                        → INSERT INTO notifications
                        → SimpMessagingTemplate.convertAndSendToUser()  ← push WebSocket
```

**Config:** `ActiveMQConfig.java` tạo `ActiveMQConnectionFactory` (embedded broker dev: `vm://localhost?broker.persistent=false`), `JmsTemplate`, và `Queue` bean tên `"booking.notifications"`.

`BookingNotificationMessage` implement `Serializable` — bắt buộc để JMS serialize object qua wire.

`@JmsListener(destination = "booking.notifications")` — Spring tự tạo listener container, xử lý deserialization.

---

### Feature 6: RabbitMQ — Tour Promotion Fanout

#### Kịch bản demo

```
1. Mở RabbitMQ Management: http://localhost:15672 (guest/guest)
   → Thấy exchange "tour.promotions" (fanout), 2 queues bound

2. Admin tạo/activate tour mới tại /admin/tours/new

3. Kiểm tra:
```
```bash
# Log listener ghi nhận
grep "TourPromotionLogListener" logs/app.log
# → Tour promoted: [Tên tour] - ID: 42

# Notifications tạo cho tất cả active users
mysql -u root -p'Aa@123456' booking_tours \
  -e "SELECT COUNT(*) FROM notifications WHERE type='TOUR_PROMOTION';"
```
```
4. RabbitMQ UI → Queues → thấy message count = 0 (đã consumed)
```

#### Cách implement

**Fanout Exchange** = broadcast: 1 message → N queues đồng thời.

```
TourServiceImpl.create() [if ACTIVE]
    → TourPromotionPublisher.publishNewTour(msg)
        → RabbitTemplate.convertAndSend("tour.promotions", "", msg)
            → FanoutExchange broadcast
                ├── tour.promo.notification.queue
                │       → TourPromotionNotificationListener
                │           → NotificationService.broadcastTourPromotion()
                │               → batch INSERT notifications cho tất cả users
                └── tour.promo.log.queue
                        → TourPromotionLogListener
                            → log.info("Tour promoted: ...")
```

`RabbitMQConfig.java` định nghĩa:
- `FanoutExchange("tour.promotions")` — routing key bị ignore
- 2 `Queue` + 2 `Binding` (mỗi queue bind vào exchange)
- `Jackson2JsonMessageConverter` — serialize message thành JSON thay vì Java binary

`broadcastTourPromotion()` dùng `saveAll()` để batch insert notifications, tránh N+1 queries.

---

## Day 3 — Realtime (WebSocket/STOMP) + Scheduler

### Feature 7: WebSocket / STOMP — Real-time Notification Push

#### Kịch bản demo

```
1. Mở DevTools → Console tab
2. Đăng nhập, vào bất kỳ trang nào

3. Trong Console thấy:
   >> CONNECTED
   subscribe /user/queue/notifications

4. [Tab khác] Admin confirms booking của user này

5. Quay lại tab user — không reload trang:
   - Badge chuông tăng từ 0 → 1
   - Toast popup hiện "Đặt tour đã được xác nhận"
   - Console: Received notification: {type: "BOOKING_CONFIRMED", ...}

6. Click chuông → /profile/notifications → đánh dấu đã đọc → badge về 0
```

#### Cách implement

**Kiến trúc STOMP over WebSocket:**

```
Browser                    Server
  |                           |
  |-- SockJS connect /ws --→  WebSocketConfig (STOMP endpoint)
  |← CONNECTED ←-----------  Simple Broker (/topic, /user/queue)
  |                           |
  |-- SUBSCRIBE /user/queue/notifications → đăng ký nhận message riêng
  |                           |
  |        [Admin confirms booking]
  |                           |
  |                    SimpMessagingTemplate
  |                    .convertAndSendToUser(email, "/queue/notifications", dto)
  |← MESSAGE ←-------------- Simple Broker route đến đúng session
  |                           |
  JS handler: update badge, show toast
```

`WebSocketConfig` cấu hình:
- STOMP endpoint `/ws` với SockJS fallback (cho browser cũ/proxy không support WebSocket)
- Application destination prefix `/app` (cho client gửi lên server)
- Broker `/topic` (broadcast) và `/user/queue` (per-user)

`notification.js` dùng SockJS + StompJS, subscribe vào `/user/queue/notifications`. Server tự route đến đúng session dựa trên `Principal` (email từ JWT).

---

### Feature 8: Scheduler — @Scheduled + @Async

> Demo chia 2 phần độc lập: **Phần A** chứng minh `@Async` (thread pool), **Phần B** chứng minh `@Scheduled` (cron jobs + audit log).

---

#### Phần A: Demo @Async — notificationExecutor thread pool

##### Bước A1 — Mở log stream trong terminal riêng

```bash
# Terminal 2: stream log real-time, lọc sẵn thread notification
tail -f logs/app.log | grep -E "notif-async|http-nio|BookingNotificationConsumer"
```

> `http-nio-8080-exec-N` = HTTP request thread.  
> `notif-async-N` = thread pool riêng của notification — đây là thứ cần xuất hiện.

##### Bước A2 — Trigger notification bằng cách xác nhận booking

```
1. Đăng nhập user thường → đặt 1 tour → booking ở trạng thái PENDING
2. Đăng nhập admin → vào /admin/bookings
3. Click "Xác nhận" booking vừa tạo
```

##### Bước A3 — Quan sát log ở Terminal 2

Trong khoảng 1–2 giây, log xuất hiện **2 dòng trên 2 thread khác nhau**:

```
[http-nio-8080-exec-5] ... AdminService - Booking 42 confirmed, publishing to ActiveMQ
[notif-async-1]        ... NotificationServiceImpl - Saving notification for userId=5
[notif-async-1]        ... NotificationServiceImpl - WebSocket push → /queue/notifications
```

**Điểm kiểm tra:**
- Dòng đầu: `http-nio-*` → HTTP request thread xử lý xong và trả về 200 **ngay lập tức**
- Dòng sau: `notif-async-*` → lưu DB + push WebSocket chạy **bất đồng bộ**, không giữ request thread

##### Bước A4 — Xác nhận notification đã được lưu DB

```sql
-- Chạy trong mysql client
SELECT n.type, n.title, n.is_read, n.created_at
FROM notifications n
WHERE n.user_id = (SELECT id FROM users WHERE email = 'user@example.com')
ORDER BY n.created_at DESC LIMIT 3;
-- → BOOKING_CONFIRMED | Đặt tour đã được xác nhận | 0 | 2026-08-04 ...
```

##### Bước A5 — Kiểm tra thread pool configuration

```bash
# Xem cấu hình trong source để giải thích con số
grep -A 8 "notificationExecutor" \
  src/main/java/com/sunasterisk/bookingtours/config/AsyncConfig.java
# → corePoolSize=3, maxPoolSize=5, queueCapacity=100, prefix="notif-async-"
```

---

#### Phần B: Demo @Scheduled — AutoCompleteBookingJob + PendingPaymentCleanupJob

> Jobs thật chạy lúc 00:30 và 01:00 — không chờ được trong demo.  
> Cách demo: **chuẩn bị data stale trước**, đổi cron sang `fixedDelay` ngắn, restart app, quan sát.

##### Bước B1 — Chuẩn bị data cho AutoCompleteBookingJob

```sql
-- Tạo booking CONFIRMED với tour có departure_date đã qua
-- (booking này lẽ ra phải được auto-complete đêm qua nhưng giả vờ chưa xử lý)
UPDATE tours SET departure_date = '2026-01-15' WHERE id = 1;

-- Xác nhận booking tồn tại và vẫn CONFIRMED
SELECT b.id, b.status, t.departure_date
FROM bookings b JOIN tours t ON b.tour_id = t.id
WHERE b.tour_id = 1 AND b.status = 'CONFIRMED';
-- → id: 7 | status: CONFIRMED | departure_date: 2026-01-15 (đã qua)
```

##### Bước B2 — Chuẩn bị data cho PendingPaymentCleanupJob

```sql
-- Tạo booking PENDING cũ hơn 48h (simulate user đặt xong rồi bỏ không thanh toán)
UPDATE bookings SET created_at = NOW() - INTERVAL 50 HOUR
WHERE status = 'PENDING' AND id = 3;

-- Xác nhận
SELECT id, status, created_at FROM bookings WHERE id = 3;
-- → id: 3 | status: PENDING | created_at: 2026-08-02 09:xx:xx (50h trước)
```

##### Bước B3 — Đổi cron sang fixedDelay để demo ngay (không chờ đêm)

Trong `AutoCompleteBookingJob.java`, tạm thời đổi annotation:

```java
// Trước (production cron)
@Scheduled(cron = "0 30 0 * * *")

// Trong demo — chạy 15 giây một lần để thấy ngay kết quả
@Scheduled(fixedDelay = 15_000)
```

Tương tự với `PendingPaymentCleanupJob.java`:
```java
@Scheduled(fixedDelay = 20_000)
```

Restart app:
```bash
# Ctrl+C để dừng → khởi động lại
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

##### Bước B4 — Quan sát log job chạy

```bash
# Terminal 2: grep scheduler logs
grep -E "AutoCompleteBookingJob|PendingPaymentCleanupJob" logs/app.log | tail -10
```

Sau khoảng 15–20 giây kể từ khi app start:

```
[scheduling-1] AutoCompleteBookingJob   - [AutoCompleteBookingJob] Starting — checking CONFIRMED bookings past departure date
[scheduling-1] AutoCompleteBookingJob   - [AutoCompleteBookingJob] Completed 1 bookings in 87ms
[scheduling-1] PendingPaymentCleanupJob - [PendingPaymentCleanupJob] Cancelling PENDING bookings created before 2026-08-02T10:...
[scheduling-1] PendingPaymentCleanupJob - [PendingPaymentCleanupJob] Cancelled 1 stale bookings in 64ms
```

**Điểm kiểm tra:** cả hai job chạy trên thread `scheduling-1` — Spring tạo single-threaded scheduler executor theo mặc định.

##### Bước B5 — Xác nhận booking đã đổi status

```sql
-- AutoCompleteBookingJob: CONFIRMED → COMPLETED
SELECT id, status FROM bookings WHERE tour_id = 1;
-- → id: 7 | status: COMPLETED  ✅

-- PendingPaymentCleanupJob: PENDING quá hạn → CANCELLED
SELECT id, status, created_at FROM bookings WHERE id = 3;
-- → id: 3 | status: CANCELLED  ✅
```

##### Bước B6 — Xem audit log trong scheduled_job_logs

```sql
SELECT job_name, status, records_processed, duration_ms, executed_at
FROM scheduled_job_logs
ORDER BY executed_at DESC LIMIT 6;
```

Kết quả mẫu:

```
AutoCompleteBookingJob    | SUCCESS | 1 | 87  | 2026-08-04 12:01:35
PendingPaymentCleanupJob  | SUCCESS | 1 | 64  | 2026-08-04 12:01:50
AutoCompleteBookingJob    | SUCCESS | 0 | 12  | 2026-08-04 12:01:50
PendingPaymentCleanupJob  | SUCCESS | 0 | 8   | 2026-08-04 12:02:05
```

> Lần chạy thứ 2: `records_processed = 0` — data đã xử lý hết, job vẫn chạy nhưng không có gì để update. Đây là hành vi đúng.

##### Bước B7 — Khôi phục cron production

Sau khi demo xong, revert lại cron thật:

```java
// AutoCompleteBookingJob
@Scheduled(cron = "0 30 0 * * *")

// PendingPaymentCleanupJob
@Scheduled(cron = "0 0 1 * * *")
```

---

#### Cách implement

**`@Scheduled`** — kích hoạt bằng `@EnableScheduling` trên `@Configuration` class:
- `AutoCompleteBookingJob`: `cron = "0 30 0 * * *"` — 0:30 sáng, query `CONFIRMED` bookings có `tour.departure_date < today` → `saveAll()` với status `COMPLETED` trong một transaction
- `PendingPaymentCleanupJob`: `cron = "0 0 1 * * *"` — 1:00 sáng, query `PENDING` bookings có `createdAt < now() - 48h` → `saveAll()` với status `CANCELLED`

Mỗi job bọc trong `try/catch`: cả success lẫn failure đều ghi 1 row vào `scheduled_job_logs` với `job_name`, `status` (SUCCESS/FAILED), `records_processed`, `duration_ms`. Không bao giờ để lỗi job làm crash app — exception được catch và log, scheduler tiếp tục lần sau.

**`@Async("notificationExecutor")`** — `AsyncConfig.java` khai báo `ThreadPoolTaskExecutor` riêng:
- `corePoolSize=3, maxPoolSize=5, queueCapacity=100` — đủ cho tải notification bình thường
- `threadNamePrefix="notif-async-"` — tên thread xuất hiện trong log, trace dễ
- `waitForTasksToCompleteOnShutdown=true` — graceful shutdown, không mất notification đang gửi

`NotificationServiceImpl.saveNotification()` annotate `@Async("notificationExecutor")`: khi caller (`BookingNotificationConsumer`, `TourPromotionNotificationListener`) gọi method này, Spring tạo proxy, tách task sang thread pool — HTTP/JMS listener thread trả về ngay, không chờ DB insert + WebSocket push.

`broadcastTourPromotion()` cũng dùng cùng executor vì RabbitMQ listener thread không nên bị block bởi I/O lâu.

---

## Day 4 — File Handling, Multithread & SOAP

### Feature 9: Apache POI — Booking Excel Export

#### Kịch bản demo

```
1. Đăng nhập admin → vào /admin/bookings
2. Có thể filter: status=CONFIRMED, date range
3. Click nút "Export Excel"
4. File tải về: bookings-2026-08-04.xlsx

5. Mở file trong Excel:
   - Hàng 1: Header bold, nền xanh nhạt, có border
   - Các cột: Booking Code | User Email | Tour Name | Participants | Total Price (VND) | Status | Departure Date | Created Date
   - Dữ liệu đúng với filter đã chọn
   - Cột tự động điều chỉnh độ rộng (autoSizeColumn)
```

#### Cách implement

`BookingExcelExporter` dùng Apache POI `XSSFWorkbook`:

```java
// Tạo workbook + sheet
XSSFWorkbook workbook = new XSSFWorkbook();
Sheet sheet = workbook.createSheet("Bookings");

// Style header: bold font + light blue fill + border
CellStyle headerStyle = workbook.createCellStyle();
Font font = workbook.createFont(); font.setBold(true);
headerStyle.setFont(font);
headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

// Ghi dữ liệu từng row
for (Booking b : bookings) {
    Row row = sheet.createRow(rowNum++);
    row.createCell(0).setCellValue(b.getBookingCode());
    // ...
}

// Auto-size columns
for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
```

Controller endpoint `GET /admin/bookings/export` set response header:
- `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- `Content-Disposition: attachment; filename=bookings-YYYY-MM-DD.xlsx`

→ browser tự kích hoạt dialog tải file.

---

### Feature 10: Apache POI + ThreadPool — Tour Excel Import

#### Kịch bản demo

```
1. Admin vào /admin/tours/import
2. Download template → mở file, thấy cột:
   Title | Description | Price | Duration Days | Max Participants | Departure Location | Destination | Departure Date | Category Name

3. Điền 20 dòng dữ liệu (15 hợp lệ + 5 lỗi cố ý):

**15 dòng hợp lệ:**

| # | Title | Description | Price | Duration Days | Max Participants | Departure Location | Destination | Departure Date | Category Name |
|---|-------|-------------|-------|--------------|------------------|--------------------|-------------|----------------|---------------|
| 2 | Phú Quốc 4N3Đ | Khám phá đảo ngọc | 3500000 | 4 | 20 | Hà Nội | Phú Quốc, Kiên Giang | 2026-09-15 | Du lịch biển |
| 3 | Sapa mùa lúa chín | Trekking bản làng, ruộng bậc thang | 2800000 | 3 | 15 | Hà Nội | Sapa, Lào Cai | 2026-09-20 | Du lịch núi |
| 4 | Đà Nẵng - Hội An 3N2Đ | Phố cổ, bãi biển Mỹ Khê | 2200000 | 3 | 25 | TP. Hồ Chí Minh | Đà Nẵng - Hội An | 2026-10-01 | Du lịch biển |
| 5 | Vịnh Hạ Long 2N1Đ | Cruise, hang động | 1800000 | 2 | 30 | Hà Nội | Vịnh Hạ Long | 2026-09-28 | Du lịch biển |
| 6 | Trekking Fansipan | Leo đỉnh Đông Dương | 4200000 | 2 | 10 | Hà Nội | Fansipan, Sapa | 2026-10-10 | Du lịch mạo hiểm |
| 7 | Mũi Né kite & surf | Lướt ván diều, cồn cát | 1900000 | 3 | 20 | TP. Hồ Chí Minh | Mũi Né, Bình Thuận | 2026-10-05 | Du lịch mạo hiểm |
| 8 | Huế Cố Đô 4N3Đ | Di tích triều Nguyễn, ẩm thực Huế | 2600000 | 4 | 20 | TP. Hồ Chí Minh | Huế | 2026-10-15 | Du lịch văn hóa |
| 9 | Hà Nội phố cổ | Phố 36 phường, Làng gốm Bát Tràng | 1500000 | 2 | 25 | TP. Hồ Chí Minh | Hà Nội | 2026-09-25 | Du lịch văn hóa |
| 10 | Côn Đảo nghỉ dưỡng 5N4Đ | Resort 5*, lặn biển ngắm san hô | 8500000 | 5 | 12 | TP. Hồ Chí Minh | Côn Đảo, Bà Rịa-VT | 2026-11-01 | Du lịch nghỉ dưỡng |
| 11 | Đà Lạt mùa hoa 3N2Đ | Thung lũng hoa, đồi chè | 2100000 | 3 | 20 | TP. Hồ Chí Minh | Đà Lạt, Lâm Đồng | 2026-10-20 | Du lịch nghỉ dưỡng |
| 12 | Ninh Bình - Tràng An | Chùa Bái Đính, chèo thuyền | 1200000 | 2 | 30 | Hà Nội | Ninh Bình | 2026-09-30 | Du lịch văn hóa |
| 13 | Hồ Ba Bể mạo hiểm | Kayak hồ Ba Bể, cắm trại | 3100000 | 3 | 15 | Hà Nội | Hồ Ba Bể, Bắc Kạn | 2026-10-08 | Du lịch mạo hiểm |
| 14 | Phong Nha-Kẻ Bàng | Hang động, trekking rừng | 5500000 | 4 | 8 | Đà Nẵng | Phong Nha, Quảng Bình | 2026-11-10 | Du lịch mạo hiểm |
| 15 | Nha Trang beach & dive | Lặn biển, tour đảo | 3200000 | 4 | 25 | Hà Nội | Nha Trang, Khánh Hoà | 2026-10-25 | Du lịch biển |
| 16 | Mộc Châu hoa mận | Ngắm hoa mận, thác Dải Yếm | 2400000 | 3 | 20 | Hà Nội | Mộc Châu, Sơn La | 2026-09-22 | Du lịch núi |

**5 dòng lỗi cố ý** (để demo error handling):

| # | Title | Price | Category Name | Lỗi mong đợi |
|---|-------|-------|---------------|--------------|
| 17 | Đảo Lý Sơn mùa tỏi | **-500000** | Du lịch biển | `Price must be greater than 0` |
| 18 | Bản Giốc Cao Bằng | **-200000** | Du lịch núi | `Price must be greater than 0` |
| 19 | Khám phá hang Én | 6500000 | **Du lịch thiên nhiên** | `Category 'Du lịch thiên nhiên' not found` |
| 20 | Đảo Nam Du xanh biếc | 2800000 | **Khám phá đảo** | `Category 'Khám phá đảo' not found` |
| 21 | **Phú Quốc 4N3Đ** | 3800000 | Du lịch biển | `Title 'Phú Quốc 4N3Đ' already exists` ← trùng row 2 |

> **Lưu ý row 21:** lỗi "already exists" được kiểm tra ở tầng Service (query DB) chứ không phải trong thread parse — chứng minh 2 lớp validation tách biệt.

4. Upload file → app xử lý

5. Kết quả hiển thị:
   ✅ 15 tours imported successfully (status: INACTIVE)
   ❌ 5 rows failed:
      Row 3: Price must be positive
      Row 7: Category 'XYZ' not found
      ...

6. Kiểm tra DB:
```
```sql
SELECT * FROM tour_import_jobs ORDER BY created_at DESC LIMIT 1;
-- → status: COMPLETED, total_rows: 20, success_rows: 15, failed_rows: 5

SELECT title, status FROM tours ORDER BY created_at DESC LIMIT 15;
-- → 15 tours mới, status: INACTIVE
```

#### Cách implement

**Parallel processing** với `ThreadPoolTaskExecutor`:

```
Upload file (.xlsx)
    → ExcelImportServiceImpl
        → Tạo TourImportJob (status: PROCESSING)
        → TourExcelImporter.importRows(file)
            → Đọc từng row bằng POI
            → Mỗi row → submit Callable<ImportRowResult> vào importExecutor (5-10 threads)
            → futures = executor.submit(callable) × N rows
            → Collect tất cả Future.get() → aggregate success/fail
        → Update TourImportJob (status: COMPLETED, success_rows, failed_rows, error_details)
```

`importExecutor` bean: `corePoolSize=5, maxPoolSize=10, queueCapacity=50` — xử lý 5–10 rows song song, phù hợp I/O bound (DB check category, duplicate title).

Validation mỗi row: required fields, `price > 0`, `durationDays > 0`, category tồn tại trong DB, title chưa dùng. Lỗi được collect thành `error_details` JSON ghi vào `tour_import_jobs.error_details`.

**`@ExcelColumn` annotation + generic reflection-based mapper** (Day 4 bonus): thay vì hardcode column index, dùng annotation trên field → mapper tự đọc column bằng reflection → dễ thêm/bớt cột mà không sửa parser.

---

### Feature 11: SOAP Web Service — Currency Conversion

#### Kịch bản demo

**Demo 1 — WSDL endpoint:**
```
Mở: http://localhost:8080/ws/currency.wsdl
→ Thấy XML WSDL với operation CurrencyConversion, input/output message types
```

**Demo 2 — Gọi SOAP qua curl:**
```bash
curl -s -X POST http://localhost:8080/ws/currency \
  -H "Content-Type: text/xml" \
  -d '<?xml version="1.0"?>
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:cur="http://bookingtours.sunasterisk.com/currency">
  <soapenv:Body>
    <cur:CurrencyConversionRequest>
      <cur:amount>1000000</cur:amount>
      <cur:fromCurrency>VND</cur:fromCurrency>
      <cur:toCurrency>USD</cur:toCurrency>
    </cur:CurrencyConversionRequest>
  </soapenv:Body>
</soapenv:Envelope>' | xmllint --format -
# → convertedAmount: 39.22, rate: 0.0000392...
```

**Demo 3 — Tour detail page:**
```
Mở /tours/1
→ Thấy:
   Giá: 2,500,000 VND
        ≈ 98.04 USD
        ≈ 89.93 EUR
```

#### Cách implement

**Contract-first SOAP** với Spring WS:

1. **`currency.xsd`** định nghĩa XML schema:
   - `CurrencyConversionRequest`: `amount` (decimal), `fromCurrency`, `toCurrency`
   - `CurrencyConversionResponse`: `convertedAmount`, `rate`, `fromCurrency`, `toCurrency`

2. **`WebServiceConfig`** expose WSDL tự động từ XSD:
   ```java
   @Bean
   DefaultWsdl11Definition currencyWsdl(XsdSchema schema) {
       DefaultWsdl11Definition def = new DefaultWsdl11Definition();
       def.setPortTypeName("CurrencyPort");
       def.setLocationUri("/ws/currency");
       def.setTargetNamespace("http://bookingtours.sunasterisk.com/currency");
       def.setSchema(schema);
       return def;
   }
   ```

3. **`CurrencyConversionEndpoint`** — `@Endpoint` xử lý SOAP request:
   ```java
   @PayloadRoot(namespace = NAMESPACE_URI, localPart = "CurrencyConversionRequest")
   @ResponsePayload
   public CurrencyConversionResponse convert(@RequestPayload CurrencyConversionRequest req) {
       // Tính qua VND làm đơn vị trung gian
       BigDecimal inVnd = req.getAmount().multiply(rateProvider.getToVnd(req.getFromCurrency()));
       BigDecimal result = inVnd.divide(rateProvider.getToVnd(req.getToCurrency()), 2, HALF_UP);
       // ...
   }
   ```

4. **`CurrencyConversionClient`** extends `WebServiceGatewaySupport` — gọi chính mình qua SOAP (self-call để demo pattern client-server). `TourController` inject client, gọi convert VND→USD và VND→EUR, truyền vào Thymeleaf model.

---

## Tổng quan kiến trúc tích hợp

```
┌─────────────────────────────────────────────────────────────┐
│                        HTTP Request                          │
│              MdcLoggingFilter (requestId, email)            │
└──────────────────────────┬──────────────────────────────────┘
                           │
          ┌────────────────▼─────────────────┐
          │         Spring MVC Controllers    │
          │    (@Tag @Operation → Swagger UI) │
          └──────┬─────────────┬─────────────┘
                 │             │
    ┌────────────▼──┐   ┌──────▼──────────────┐
    │  BookingService│   │    TourService       │
    │                │   │                     │
    │ confirmBooking()│   │ create() / update() │
    └───────┬────────┘   └──────┬──────────────┘
            │                   │
    ┌───────▼───────┐   ┌───────▼───────────┐
    │ ActiveMQ Queue │   │ RabbitMQ Fanout   │
    │booking.notif. │   │ tour.promotions   │
    └───────┬───────┘   └──┬────────────────┘
            │               │        │
    ┌───────▼──────┐  ┌─────▼──┐ ┌───▼──────────┐
    │Notification  │  │Notif   │ │Log Listener  │
    │Consumer      │  │Listener│ │(SLF4J log)   │
    └───────┬──────┘  └───┬────┘ └──────────────┘
            │             │
    ┌───────▼─────────────▼──────┐
    │    NotificationService     │
    │  @Async("notificationExec")│
    │  saveNotification() → DB   │
    │  convertAndSendToUser()    │
    └──────────────┬─────────────┘
                   │ STOMP push
    ┌──────────────▼─────────────┐
    │  Browser WebSocket Client  │
    │  notification.js → badge   │
    └────────────────────────────┘

    ┌─────────────────────────────┐
    │  @Scheduled Jobs            │
    │  AutoCompleteBookingJob     │  → 0:30 AM daily
    │  PendingPaymentCleanupJob   │  → 1:00 AM daily
    │  → scheduled_job_logs table │
    └─────────────────────────────┘

    ┌─────────────────────────────┐
    │  Excel Import (POI)         │
    │  TourExcelImporter          │
    │  importExecutor (5-10 th.)  │  → parallel row validation
    │  TourImportJob → DB         │
    └─────────────────────────────┘

    ┌─────────────────────────────┐
    │  SOAP Currency Service      │
    │  /ws/currency (endpoint)    │
    │  CurrencyConversionClient   │  → TourController → detail page
    └─────────────────────────────┘
```

---

## Quick Verification Commands

```bash
# 1. Flyway migrations OK
mvn spring-boot:run -Dspring-boot.run.profiles=dev 2>&1 | grep "Successfully applied"

# 2. Swagger UI
open http://localhost:8080/swagger-ui.html

# 3. SOAP WSDL
curl -s http://localhost:8080/ws/currency.wsdl | grep "wsdl:definitions"

# 4. Log structured
tail -5 logs/app.log | grep -E "\[.*\] \[.*\]"

# 5. Notifications table
mysql -u root -p'Aa@123456' booking_tours -e "SELECT COUNT(*) FROM notifications;"

# 6. Import jobs
mysql -u root -p'Aa@123456' booking_tours -e "SELECT * FROM tour_import_jobs LIMIT 5;"

# 7. Scheduler logs
mysql -u root -p'Aa@123456' booking_tours -e "SELECT job_name, status, records_processed FROM scheduled_job_logs;"

# 8. RabbitMQ alive
curl -s -u guest:guest http://localhost:15672/api/queues | python3 -m json.tool | grep '"name"'
```
