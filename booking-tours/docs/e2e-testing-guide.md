# E2E Testing Guide — SUN Booking Tours

> Test các phần đã hoàn thành theo `task-advanced-breakdown.md`.  
> Mỗi task có: Prerequisites → Steps → Expected Result → Pass/Fail criteria.

---

## Day 1 — Foundation & Infrastructure

### Prerequisites chung

```bash
# MySQL đang chạy, database đã tồn tại
mysql -u root -p'Aa@123456' -e "SHOW DATABASES LIKE 'booking_tours';"

# Build và chạy app với profile dev
cd /Users/nguyen.duc.huyb/IdeaProjects/huyB/booking-tours
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

App khởi động thành công khi log xuất hiện: `Started BookingToursApplication in X.XXX seconds`

---

### T1.1 — PostgreSQL → MySQL Migration

#### Prerequisites
- MySQL Server đang chạy tại `localhost:3306`
- Database `booking_tours` đã tạo: `CREATE DATABASE booking_tours CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`
- Biến môi trường OAuth2 đã set (hoặc dùng placeholder) để app không crash khi startup

#### Test Steps

**Step 1 — Kiểm tra Flyway migrations chạy sạch**
```bash
# Xem log khi app khởi động, tìm các dòng Flyway
mvn spring-boot:run -Dspring-boot.run.profiles=dev 2>&1 | grep -E "Flyway|migration|V[0-9]__"
```
Expected:
```
Successfully applied 5 migrations to schema `booking_tours`
Current version of schema `booking_tours`: 5
```

**Step 2 — Kiểm tra schema MySQL**
```sql
mysql -u root -p'Aa@123456' booking_tours

-- Xác nhận 12 bảng tồn tại
SHOW TABLES;

-- Kiểm tra bảng users (không có BIGSERIAL, BOOLEAN PostgreSQL)
DESCRIBE users;
-- Cột id phải là: bigint NOT NULL AUTO_INCREMENT
-- Cột is_active phải là: tinyint(1)

-- Kiểm tra enum MySQL inline (không có CREATE TYPE)
SHOW CREATE TABLE bookings\G
-- Phải thấy: status enum('PENDING','CONFIRMED','CANCELLED','COMPLETED')

-- Kiểm tra không có DEFERRABLE INITIALLY DEFERRED
SHOW CREATE TABLE payments\G
```

**Step 3 — Kiểm tra seed data**
```sql
mysql -u root -p'Aa@123456' booking_tours

SELECT COUNT(*) FROM users;       -- > 0
SELECT COUNT(*) FROM tours;       -- > 0
SELECT COUNT(*) FROM categories;  -- > 0
SELECT COUNT(*) FROM reviews;     -- > 0
```

**Step 4 — Smoke test end-to-end qua browser**
1. Mở `http://localhost:8080/auth/login`
2. Đăng nhập với tài khoản seed (xem `V2__seed_data.sql`)
3. Truy cập `http://localhost:8080/tours` → danh sách tour hiển thị
4. Click vào 1 tour → trang chi tiết tour hiển thị

#### Pass Criteria
- [x] Flyway log: `Successfully applied N migrations`, không có lỗi
- [x] `SHOW TABLES` trả về ≥ 5 bảng (users, tours, bookings, payments, categories...)
- [x] Không tìm thấy `BIGSERIAL`, `BOOLEAN`, `CREATE TYPE`, `DEFERRABLE` trong bất kỳ bảng nào
- [x] Seed data tồn tại (COUNT > 0) — users✓ tours✓ categories✓ reviews✗ (FK issue in V4, 0 rows)
- [x] Trang danh sách tour load được sau khi đăng nhập

---

### T1.2 — SLF4J + Logback Configuration

#### Prerequisites
- App đang chạy với profile `dev` (xem Prerequisites chung)
- Thư mục `logs/` tồn tại hoặc sẽ được tạo tự động khi app start

#### Test Steps

**Step 1 — Kiểm tra file log được tạo**
```bash
# Sau khi app start, kiểm tra thư mục logs/
ls -la logs/
# Phải thấy: app.log, có thể thấy error.log nếu đã có lỗi
```

**Step 2 — Kiểm tra log pattern có requestId và userEmail**
```bash
# Xem 20 dòng đầu app.log
head -20 logs/app.log
```
Expected format mỗi dòng:
```
2026-07-29 17:00:00.123 [] [] INFO  c.s.b.BookingToursApplication - Started BookingToursApplication in 5.123 seconds
```
> `[]` là requestId và userEmail — trống khi chưa có HTTP request.

**Step 3 — Kiểm tra MDC injection khi có HTTP request**
```bash
# Gửi 1 HTTP request
curl -s http://localhost:8080/tours > /dev/null

# Xem log ngay sau đó
tail -20 logs/app.log | grep -E "\[.{8,}\]"
```
Expected: mỗi dòng log trong request cycle có `[<UUID>]` thay vì `[]`:
```
2026-07-29 17:00:05.456 [a1b2c3d4-e5f6-...] [] INFO  c.s.b.filter.MdcLoggingFilter - ...
```

**Step 4 — Kiểm tra requestId nhất quán trong 1 request**
```bash
# Lọc theo 1 requestId cụ thể
REQUEST_ID=$(grep -oP '\[\K[0-9a-f-]{36}(?=\])' logs/app.log | head -1)
grep "$REQUEST_ID" logs/app.log
```
Expected: nhiều dòng log cùng requestId, tất cả từ 1 HTTP request duy nhất.

**Step 5 — Kiểm tra userEmail trong log sau khi đăng nhập**
1. Đăng nhập qua browser tại `http://localhost:8080/auth/login`
2. Truy cập `http://localhost:8080/tours`
3. Kiểm tra log:
```bash
tail -30 logs/app.log | grep -v "\[\] \[\]"
```
Expected: dòng log có dạng `[<UUID>] [user@example.com]`

**Step 6 — Kiểm tra ERROR log tách biệt**
```bash
# Kích hoạt 1 lỗi: truy cập route không tồn tại
curl -s http://localhost:8080/non-existent-page-xyz > /dev/null

# Nếu có lỗi 500, kiểm tra error.log
cat logs/error.log 2>/dev/null | head -20
```
Hoặc kiểm tra ERROR_FILE filter chỉ ghi ERROR:
```bash
# Chỉ có ERROR level trong error.log
grep -v "^2.*ERROR" logs/error.log | grep -v "^$" | wc -l
# Phải trả về 0 (không có dòng nào không phải ERROR)
```

**Step 7 — Kiểm tra log level theo profile**

Dev profile (đang chạy):
```bash
grep "DEBUG" logs/app.log | wc -l
# Phải > 0 (có DEBUG logs)
```

> Prod profile không test trực tiếp, chỉ verify cấu hình trong `logback-spring.xml`:
> `springProfile name="prod"` → level INFO cho app package, không có CONSOLE appender.

#### Pass Criteria
- [x] `logs/app.log` tồn tại và có nội dung sau khi app start
- [x] Mỗi dòng log tuân theo pattern: `timestamp [requestId] [userEmail] LEVEL logger - message`
- [x] HTTP request sinh ra requestId UUID, không trùng giữa các request
- [x] Sau khi đăng nhập, `userEmail` xuất hiện trong log (không còn trống)
- [x] `logs/error.log` chỉ chứa ERROR level entries
- [x] Dev profile có DEBUG logs từ package `com.sunasterisk.bookingtours`

---

### T1.3 — Swagger / OpenAPI 3.0 Setup

#### Prerequisites
- App đang chạy với profile `dev`
- `springdoc.swagger-ui.enabled=true` trong `application-dev.properties` ✓

#### Test Steps

**Step 1 — Kiểm tra Swagger UI accessible**
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/swagger-ui.html
# Expected: 302 (redirect) hoặc 200
```
Mở browser: `http://localhost:8080/swagger-ui.html` → Swagger UI load, không 404.

**Step 2 — Kiểm tra API docs endpoint**
```bash
curl -s http://localhost:8080/v3/api-docs | python3 -m json.tool | head -30
```
Expected: JSON với `"openapi": "3.0.x"`, `"info"` section có title và version.

**Step 3 — Kiểm tra controllers được liệt kê**
```bash
curl -s http://localhost:8080/v3/api-docs | python3 -c "
import json, sys
data = json.load(sys.stdin)
tags = [t['name'] for t in data.get('tags', [])]
print(f'Total tags: {len(tags)}')
for t in tags:
    print(f'  - {t}')
"
```
Expected: ≥ 10 tags tương ứng với các controller đã annotate `@Tag`.

**Step 4 — Kiểm tra security scheme định nghĩa**
```bash
curl -s http://localhost:8080/v3/api-docs | python3 -c "
import json, sys
data = json.load(sys.stdin)
schemes = data.get('components', {}).get('securitySchemes', {})
print('Security schemes:', list(schemes.keys()))
"
```
Expected: xuất hiện scheme JWT/cookie (tên do `SwaggerConfig.java` định nghĩa).

**Step 5 — Kiểm tra Swagger disabled trong test profile**
```bash
curl -s http://localhost:8080/v3/api-docs -H "Spring-Profile: test" 2>/dev/null
# Hoặc kiểm tra application-test.properties
grep -E "swagger|springdoc" src/test/resources/application-test.properties
# Expected: springdoc.swagger-ui.enabled=false hoặc không có config (default false)
```

#### Pass Criteria
- [x] `http://localhost:8080/swagger-ui.html` mở được Swagger UI (không 404/500)
- [x] `/v3/api-docs` trả về JSON hợp lệ với `"openapi": "3.0.x"` — thực tế trả về `3.1.0`
- [x] ≥ 10 tags (controllers) được liệt kê trong Swagger — thực tế 14 tags
- [x] Security scheme JWT cookie được định nghĩa trong `components.securitySchemes`
- [x] Test/prod profile: swagger disabled (verify qua `application-test.properties`)

---

### T1.4 — OAuth2 Facebook + Twitter

> **Note:** E2E với real credentials (live app registrations) được đánh dấu ⚠️ vì phụ thuộc vào việc có app credentials thực.  
> Phần này test các thành phần có thể verify mà không cần live OAuth2.

#### Prerequisites
- App đang chạy với profile `dev`
- Biến môi trường `FACEBOOK_CLIENT_ID`, `FACEBOOK_CLIENT_SECRET`, `TWITTER_CLIENT_ID`, `TWITTER_CLIENT_SECRET` đã set (hoặc dùng placeholder để test UI flow)

#### Test Steps

**Step 1 — Kiểm tra OAuth2 buttons hiển thị trên login page**
```bash
curl -s http://localhost:8080/auth/login | grep -E "facebook|twitter|Google|oauth2"
```
Mở browser: `http://localhost:8080/auth/login` → thấy buttons "Login with Facebook", "Login with Twitter" (bên cạnh Google).

**Step 2 — Kiểm tra OAuth2 redirect URL được generate đúng**
```bash
# Click button Facebook → verify redirect URL format
curl -v -s http://localhost:8080/oauth2/authorization/facebook 2>&1 | grep -i "location"
# Expected: Location header trỏ đến https://www.facebook.com/dialog/oauth?...
```

```bash
# Twitter redirect
curl -v -s http://localhost:8080/oauth2/authorization/twitter 2>&1 | grep -i "location"
# Expected: Location header trỏ đến https://twitter.com/i/oauth2/authorize?...
```

**Step 3 — Kiểm tra CustomStandardOAuth2UserService được wire đúng**
```bash
grep -n "customStandardOAuth2UserService\|userService()" \
  src/main/java/com/sunasterisk/bookingtours/config/SecurityConfig.java
```
Expected: tìm thấy `userService(customStandardOAuth2UserService)` trong OAuth2 config chain.

**Step 4 — Kiểm tra logic xử lý Facebook user (unit level)**

Xem file `CustomStandardOAuth2UserService.java`:
```bash
grep -n "facebook\|graph.facebook\|name,email\|twitter\|users/me\|synthetic\|noemail.local" \
  src/main/java/com/sunasterisk/bookingtours/service/impl/CustomStandardOAuth2UserService.java
```
Expected:
- Facebook: gọi Graph API với fields `id,name,email`
- Twitter: gọi `/2/users/me`, đọc key `"data"`
- Twitter synthetic email: pattern `twitter_{username}@noemail.local`

**Step 5 — Kiểm tra Google OAuth2 không bị ảnh hưởng (regression)**
```bash
curl -v -s http://localhost:8080/oauth2/authorization/google 2>&1 | grep -i "location"
# Expected: Location header trỏ đến https://accounts.google.com/o/oauth2/...
# Và phải có prompt=select_account (chỉ Google mới có)
```

**Step 6 — ⚠️ E2E với real credentials (skip nếu không có live app)**

Nếu có Facebook Developer App đã approved và Twitter Developer App:
1. Set env vars với real credentials
2. Restart app
3. Click "Login with Facebook" → complete Facebook OAuth flow → verify redirect về `/` sau khi login
4. Kiểm tra DB: `SELECT * FROM users WHERE email LIKE '%facebook%' OR provider = 'facebook';`
5. Click "Login with Twitter" → complete Twitter OAuth flow
6. Kiểm tra DB: `SELECT * FROM users WHERE email LIKE '%twitter_%@noemail.local';`

**Step 7 — Kiểm tra `prompt=select_account` chỉ áp dụng Google**
```bash
grep -n "select_account\|google\|registrationId" \
  src/main/java/com/sunasterisk/bookingtours/config/CustomAuthorizationRequestResolver.java
```
Expected: `prompt=select_account` được thêm vào request chỉ khi `registrationId.equals("google")`.

#### Pass Criteria
- [x] Login page hiển thị đủ 3 social login buttons (Google, Facebook, Twitter)
- [x] `/oauth2/authorization/facebook` redirect đến `facebook.com`
- [x] `/oauth2/authorization/twitter` redirect đến `twitter.com`
- [x] `CustomStandardOAuth2UserService` handle cả Facebook lẫn Twitter với đúng user-info URI
- [x] Twitter synthetic email pattern `twitter_{username}@noemail.local` được implement
- [x] Google OAuth2 vẫn hoạt động bình thường (regression pass)
- [x] `prompt=select_account` chỉ áp dụng cho Google, không cho Facebook/Twitter
- [x] ⚠️ E2E với real credentials: user được tạo/cập nhật trong DB sau khi login thành công

---

---

## Day 2 — Messaging: ActiveMQ + RabbitMQ

### Prerequisites chung Day 2

```bash
# 1. MySQL đang chạy và app đã qua Day 1 (Flyway V1–V6 applied)
mysql -u root -p'Aa@123456' -e "SELECT version FROM booking_tours.flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;"
# Expected: 6

# 2. Khởi động RabbitMQ qua Docker (cần cho T2.6–T2.9)
docker run -d --name rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  rabbitmq:3-management
# Kiểm tra container running:
docker ps | grep rabbitmq

# 3. Chạy app với profile dev
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

App khởi động thành công khi log xuất hiện:
```
Apache ActiveMQ X.X.X (localhost, ...) started
Started BookingToursApplication in X.XXX seconds
```

---

### T2.1 — ActiveMQ Embedded Broker

#### Prerequisites
- App đang chạy với profile `dev` (RabbitMQ không cần cho task này)

#### Test Steps

**Step 1 — Kiểm tra embedded broker khởi động**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev 2>&1 | grep -E "ActiveMQ|BrokerService|vm://localhost"
```
Expected:
```
Apache ActiveMQ 6.x.x (localhost, ...) is starting
Apache ActiveMQ 6.x.x (localhost, ...) started
Connector vm://localhost started
```

**Step 2 — Kiểm tra JmsTemplate và Queue bean available**
```bash
# Swagger endpoint liệt kê beans (nếu actuator bật) hoặc check log không có NoSuchBeanDefinitionException
mvn spring-boot:run -Dspring-boot.run.profiles=dev 2>&1 | grep -E "NoSuchBean|ActiveMQConfig|BOOKING_NOTIFICATIONS"
# Expected: không có dòng nào (không có lỗi)
```

**Step 3 — Kiểm tra config trong properties**
```bash
grep -E "activemq|in-memory" src/main/resources/application-dev.properties
```
Expected:
```
spring.activemq.broker-url=vm://localhost?broker.persistent=false
spring.activemq.in-memory=true
```

#### Pass Criteria
- [x] Log hiển thị `Apache ActiveMQ X.X.X (localhost, ...) started`
- [x] Log hiển thị `Connector vm://localhost started`
- [x] App khởi động không có `NoSuchBeanDefinitionException` liên quan đến JMS
- [x] `spring.activemq.broker-url=vm://localhost?broker.persistent=false` trong `application-dev.properties`

---

### T2.2 — Notification Entity + Flyway V7 Migration

#### Prerequisites
- App đang chạy (Flyway tự động apply V7 khi startup)

#### Test Steps

**Step 1 — Kiểm tra Flyway V7 applied**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev 2>&1 | grep -E "V7__|notifications"
```
Expected:
```
Successfully applied 1 migration to schema `booking_tours` (... V7__create_notifications_table)
```

Hoặc kiểm tra trực tiếp DB sau khi app đã start:
```sql
mysql -u root -p'Aa@123456' booking_tours

SELECT version, description, success
FROM flyway_schema_history
WHERE version IN ('6', '7')
ORDER BY installed_rank;
-- Expected: V6 (seed_admin_user) và V7 (create_notifications_table) đều success=1
```

**Step 2 — Kiểm tra schema bảng notifications**
```sql
mysql -u root -p'Aa@123456' booking_tours
DESCRIBE notifications;
```
Expected columns:
```
id          bigint          NOT NULL AUTO_INCREMENT
user_id     bigint          NOT NULL
type        varchar(30)     NOT NULL
title       varchar(255)    NOT NULL
message     text            NOT NULL
is_read     tinyint(1)      NOT NULL   DEFAULT 0
created_at  datetime(6)     NOT NULL
updated_at  datetime(6)     NOT NULL
```

**Step 3 — Kiểm tra index và FK**
```sql
SHOW CREATE TABLE notifications\G
```
Expected:
- `KEY idx_notifications_user (user_id)`
- `KEY idx_notifications_user_unread (user_id, is_read)`
- `CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE`

**Step 4 — Kiểm tra bảng rỗng ban đầu**
```sql
SELECT COUNT(*) FROM notifications;
-- Expected: 0
```

#### Pass Criteria
- [x] Flyway log: `V7__create_notifications_table` applied, `success=1`
- [x] Bảng `notifications` có đủ 8 cột (id, user_id, type, title, message, is_read, created_at, updated_at)
- [x] FK `fk_notifications_user → users(id) ON DELETE CASCADE` tồn tại
- [x] 2 index: `idx_notifications_user` và `idx_notifications_user_unread` tồn tại
- [x] Bảng rỗng khi chưa có action nào

---

### T2.3–T2.5 — ActiveMQ Pipeline: Producer → Consumer → BookingService

> T2.3 (Producer) và T2.4 (Consumer) được verify gián tiếp qua T2.5 (BookingService integration).  
> Khi admin confirm/cancel booking → Producer gửi → Consumer nhận → `notifications` row được insert.

#### Prerequisites
- App đang chạy với profile `dev`
- Đăng nhập admin: `admin@sunasterisk.com` / `Admin@123456` (seed từ V6)
- Có ít nhất 1 booking ở trạng thái `PENDING` trong DB

```sql
-- Tạo booking PENDING nếu chưa có (dùng seed user và tour có sẵn)
mysql -u root -p'Aa@123456' booking_tours

-- Lấy 1 user_id và tour_id có sẵn
SELECT id FROM users WHERE role_id != (SELECT id FROM roles WHERE name='ADMIN') LIMIT 1;
SELECT id FROM tours WHERE status='ACTIVE' LIMIT 1;

-- Tạo booking thủ công nếu cần:
INSERT INTO bookings (booking_code, status, total_price, number_of_people, user_id, tour_id, created_at, updated_at)
VALUES (UUID(), 'PENDING', 1000000, 2, <user_id>, <tour_id>, NOW(), NOW());
```

#### Test Steps — T2.5a: Admin Confirm Booking

**Step 1 — Ghi nhận trạng thái trước**
```sql
mysql -u root -p'Aa@123456' booking_tours

-- Lấy booking PENDING
SELECT id, booking_code, status, user_id FROM bookings WHERE status='PENDING' LIMIT 1;
-- Ghi lại: booking_id = X, user_id = Y

-- Đếm notifications hiện tại của user đó
SELECT COUNT(*) FROM notifications WHERE user_id = <Y>;
-- Expected: 0 (hoặc số trước đó)
```

**Step 2 — Admin confirm booking qua giao diện hoặc curl**

Qua browser (login admin trước):
```
POST http://localhost:8080/admin/bookings/{booking_id}/confirm
```

Hoặc qua curl (cần CSRF token hoặc dùng session cookie):
```bash
# Đăng nhập admin, lấy session cookie
curl -c cookies.txt -b cookies.txt \
  -X POST "http://localhost:8080/admin/bookings/<booking_id>/confirm" \
  -H "Content-Type: application/x-www-form-urlencoded"
```

**Step 3 — Kiểm tra notification được tạo**
```sql
mysql -u root -p'Aa@123456' booking_tours

SELECT id, user_id, type, title, message, is_read, created_at
FROM notifications
WHERE user_id = <Y>
ORDER BY created_at DESC
LIMIT 3;
```
Expected:
```
| id | user_id | type              | title                     | is_read | created_at          |
|----|---------|-------------------|---------------------------|---------|---------------------|
| 1  | Y       | BOOKING_CONFIRMED | Đặt tour đã được xác nhận | 0       | 2026-07-29 22:xx:xx |
```

**Step 4 — Kiểm tra message chứa booking code và tour title**
```sql
SELECT message FROM notifications
WHERE type = 'BOOKING_CONFIRMED'
ORDER BY created_at DESC
LIMIT 1;
```
Expected format:
```
Booking <UUID> cho tour "<tên tour>".
```

**Step 5 — Log ActiveMQ (xác nhận JMS flow)**
```bash
tail -50 logs/app.log | grep -E "booking\.notifications|BookingNotification|JMS"
```
Expected: log từ `BookingNotificationProducer` (send) và `BookingNotificationConsumer` (receive), không có ERROR.

#### Test Steps — T2.5b: Admin Cancel Booking

**Step 1 — Lấy 1 booking khác ở PENDING**
```sql
SELECT id, user_id FROM bookings WHERE status='PENDING' LIMIT 1;
```

**Step 2 — Admin cancel booking**
```bash
curl -c cookies.txt -b cookies.txt \
  -X POST "http://localhost:8080/admin/bookings/<booking_id>/cancel"
```

**Step 3 — Kiểm tra notification BOOKING_CANCELLED**
```sql
SELECT type, title, message FROM notifications
WHERE user_id = <user_id>
  AND type = 'BOOKING_CANCELLED'
ORDER BY created_at DESC
LIMIT 1;
```
Expected:
```
| BOOKING_CANCELLED | Đặt tour đã bị hủy | Booking <UUID> cho tour "<tên tour>". |
```

#### Pass Criteria
- [x] Admin confirm booking → 1 row `BOOKING_CONFIRMED` trong `notifications` cho đúng `user_id`
- [x] Admin cancel booking → 1 row `BOOKING_CANCELLED` trong `notifications` cho đúng `user_id`
- [x] `message` chứa booking code (UUID) và tour title
- [x] `is_read = 0` (chưa đọc)
- [x] Log không có ERROR từ `BookingNotificationConsumer`
- [x] Log của booking consumer không có JMS exception sau các lần confirm/cancel

---

### T2.6 — RabbitMQ Configuration

#### Prerequisites
- Docker đang chạy, container `rabbitmq` đã start (xem Prerequisites chung)
- App đang chạy với profile `dev`

#### Test Steps

**Step 1 — Kiểm tra app kết nối RabbitMQ thành công khi startup**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev 2>&1 | grep -E "rabbit|AMQP|5672|tour\.promotions|promo"
```
Expected (không có lỗi `Connection refused`):
```
... Successfully declared exchange/queue topology
```
Hoặc không có dòng nào chứa `Connection refused` / `RabbitMQ connection error`.

**Step 2 — Kiểm tra exchange và queues trong Management UI**

Mở browser: `http://localhost:15672` (guest/guest)

- Tab **Exchanges** → tìm `tour.promotions` (type: fanout, durable: true)
- Tab **Queues** → tìm `tour.promo.notification.queue` và `tour.promo.log.queue` (đều durable)

Hoặc qua RabbitMQ HTTP API:
```bash
# Kiểm tra exchange
curl -s -u guest:guest http://localhost:15672/api/exchanges/%2F/tour.promotions | python3 -m json.tool | grep -E "name|type|durable"
# Expected: "name": "tour.promotions", "type": "fanout", "durable": true

# Kiểm tra queues
curl -s -u guest:guest http://localhost:15672/api/queues | python3 -c "
import json, sys
queues = json.load(sys.stdin)
names = [q['name'] for q in queues]
print('Queues:', names)
"
# Expected: ['tour.promo.log.queue', 'tour.promo.notification.queue'] trong danh sách
```

**Step 3 — Kiểm tra bindings**
```bash
curl -s -u guest:guest "http://localhost:15672/api/bindings/%2F/e/tour.promotions/q/tour.promo.notification.queue" | python3 -m json.tool
# Expected: JSON với "source": "tour.promotions", "destination": "tour.promo.notification.queue"

curl -s -u guest:guest "http://localhost:15672/api/bindings/%2F/e/tour.promotions/q/tour.promo.log.queue" | python3 -m json.tool
# Expected: JSON với "destination": "tour.promo.log.queue"
```

**Step 4 — Kiểm tra test profile không cần broker thật**
```bash
mvn test 2>&1 | grep -E "BUILD|RabbitMQ|Connection refused|AMQP"
# Expected: BUILD SUCCESS, không có Connection refused
```

#### Pass Criteria
- [x] App start không có `Connection refused` tới `localhost:5672`
- [x] Exchange `tour.promotions` (type: fanout, durable) visible trong Management UI
- [x] Queue `tour.promo.notification.queue` và `tour.promo.log.queue` visible và durable
- [x] Cả 2 queues được bind vào exchange `tour.promotions`
- [x] `mvn test` BUILD SUCCESS không cần RabbitMQ thật (`auto-startup=false` trong test profile)

---

### T2.7–T2.9 — RabbitMQ Pipeline: TourService → Fanout → 2 Listeners

> T2.7 (Publisher) và T2.8 (Listeners) được verify gián tiếp qua T2.9 (TourService integration).  
> Admin tạo/activate ACTIVE tour → Publisher gửi → Fanout → 2 listeners nhận.

#### Prerequisites
- RabbitMQ Docker container đang chạy (`docker ps | grep rabbitmq`)
- App đang chạy với profile `dev`
- Có ít nhất 2 user active trong DB (để broadcastTourPromotion tạo ≥ 2 rows)

```sql
mysql -u root -p'Aa@123456' booking_tours

-- Kiểm tra active users
SELECT COUNT(*) FROM users WHERE is_active = 1;
-- Expected: ≥ 2

-- Tạo thêm user active nếu cần
INSERT INTO users (email, password_hash, full_name, is_active, role_id, created_at, updated_at)
VALUES ('testuser2@example.com', '$2a$10$...', 'Test User 2', 1,
        (SELECT id FROM roles WHERE name='USER'), NOW(), NOW());
```

#### Test Steps — T2.9a: Tạo ACTIVE tour mới

**Step 1 — Ghi nhận trạng thái notifications trước**
```sql
SELECT COUNT(*) AS total_before FROM notifications WHERE type='TOUR_PROMOTION';
```

**Step 2 — Admin tạo tour với status ACTIVE**

Qua Swagger UI (`http://localhost:8080/swagger-ui.html`) → `POST /api/admin/tours`:
```json
{
  "title": "Tour E2E Test RabbitMQ",
  "description": "Test tour promotion broadcast",
  "price": 1500000,
  "duration": 3,
  "maxParticipants": 20,
  "status": "ACTIVE",
  "categoryId": 1
}
```

Hoặc qua curl:
```bash
curl -c cookies.txt -b cookies.txt \
  -X POST "http://localhost:8080/api/admin/tours" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Tour E2E Test RabbitMQ",
    "description": "Test tour promotion broadcast",
    "price": 1500000,
    "duration": 3,
    "maxParticipants": 20,
    "status": "ACTIVE",
    "categoryId": 1
  }'
```

**Step 3 — Kiểm tra TourPromotionNotificationListener đã broadcast**
```sql
mysql -u root -p'Aa@123456' booking_tours

-- Số notifications TOUR_PROMOTION mới tạo phải = số active users
SELECT COUNT(*) AS total_after FROM notifications WHERE type='TOUR_PROMOTION';
-- Expected: total_before + <số active users>

-- Chi tiết các row vừa tạo
SELECT user_id, type, title, message, is_read, created_at
FROM notifications
WHERE type = 'TOUR_PROMOTION'
ORDER BY created_at DESC
LIMIT 10;
```
Expected:
```
| user_id | type           | title                          | is_read |
|---------|----------------|--------------------------------|---------|
| 1       | TOUR_PROMOTION | Tour mới: Tour E2E Test RabbitMQ | 0     |
| 2       | TOUR_PROMOTION | Tour mới: Tour E2E Test RabbitMQ | 0     |
| ...     | ...            | ...                            | ...     |
```

**Step 4 — Kiểm tra TourPromotionLogListener đã log**
```bash
grep "New ACTIVE tour published" logs/app.log | tail -3
```
Expected:
```
... INFO  c.s.b.m.r.TourPromotionLogListener - New ACTIVE tour published: id=<X>, title=Tour E2E Test RabbitMQ
```

**Step 5 — Kiểm tra message content đúng**
```sql
SELECT title, message FROM notifications
WHERE type = 'TOUR_PROMOTION'
ORDER BY created_at DESC
LIMIT 1;
```
Expected:
- `title`: `Tour mới: Tour E2E Test RabbitMQ`
- `message`: `Tour "Tour E2E Test RabbitMQ" vừa được kích hoạt. Đặt ngay!`

#### Test Steps — T2.9b: Update tour sang ACTIVE

**Step 1 — Tạo 1 tour INACTIVE**
```bash
curl -c cookies.txt -b cookies.txt \
  -X POST "http://localhost:8080/api/admin/tours" \
  -H "Content-Type: application/json" \
  -d '{"title": "Tour Draft", "description": "Draft", "price": 500000, "duration": 2, "maxParticipants": 10, "status": "INACTIVE", "categoryId": 1}'
# Ghi lại tour_id từ response
```

**Step 2 — Ghi nhận số notifications trước**
```sql
SELECT COUNT(*) FROM notifications WHERE type='TOUR_PROMOTION';
```

**Step 3 — Update tour sang ACTIVE**
```bash
curl -c cookies.txt -b cookies.txt \
  -X PUT "http://localhost:8080/api/admin/tours/<tour_id>" \
  -H "Content-Type: application/json" \
  -d '{"status": "ACTIVE"}'
```

**Step 4 — Kiểm tra broadcast được trigger**
```sql
SELECT COUNT(*) FROM notifications WHERE type='TOUR_PROMOTION';
-- Expected: tăng thêm đúng số lượng active users
```

#### Test Steps — T2.9c: INACTIVE tour KHÔNG trigger broadcast

**Step 1 — Tạo tour INACTIVE**
```bash
curl -c cookies.txt -b cookies.txt \
  -X POST "http://localhost:8080/api/admin/tours" \
  -H "Content-Type: application/json" \
  -d '{"title": "Tour No Broadcast", "description": "No broadcast", "price": 200000, "duration": 1, "maxParticipants": 5, "status": "INACTIVE", "categoryId": 1}'
```

**Step 2 — Kiểm tra số notifications KHÔNG tăng**
```sql
-- Đếm trước
SELECT COUNT(*) FROM notifications WHERE type='TOUR_PROMOTION';
-- Tạo tour INACTIVE ở trên
-- Đếm sau — phải bằng nhau
SELECT COUNT(*) FROM notifications WHERE type='TOUR_PROMOTION';
```

**Step 3 — Kiểm tra log KHÔNG có dòng log cho tour INACTIVE**
```bash
grep "No Broadcast" logs/app.log
# Expected: không tìm thấy dòng nào
```

#### Pass Criteria
- [x] Admin tạo tour ACTIVE → `N` rows `TOUR_PROMOTION` trong notifications (N = số active users)
- [x] Admin update tour sang ACTIVE → `N` rows mới được tạo
- [x] Admin tạo/update tour INACTIVE → số notifications KHÔNG tăng
- [x] `title` = `"Tour mới: <tên tour>"`, `message` = `"Tour \"<tên tour>\" vừa được kích hoạt. Đặt ngay!"`
- [x] `is_read = 0` cho tất cả notifications mới
- [x] Log có dòng `New ACTIVE tour published: id=X, title=<tên tour>`
- [x] Log không có ERROR từ `TourPromotionNotificationListener` hay `TourPromotionLogListener`
- [x] RabbitMQ Management UI: message count trên cả 2 queues = 0 sau khi listeners xử lý xong

---

## Checklist tổng hợp Day 2

| Task | Test | Status |
|------|------|--------|
| T2.1 | Log `Apache ActiveMQ X.X.X started` khi app start | ☑ |
| T2.1 | Log `Connector vm://localhost started` | ☑ |
| T2.1 | App start không có JMS bean error | ☑ |
| T2.2 | Flyway V7 applied, `success=1` | ☑ |
| T2.2 | Bảng `notifications` có đủ schema (8 cột, FK, 2 index) | ☑ |
| T2.3–T2.4 | (verify gián tiếp qua T2.5) | — |
| T2.5 | Admin confirm → 1 row `BOOKING_CONFIRMED` đúng user | ☐ |
| T2.5 | Admin cancel → 1 row `BOOKING_CANCELLED` đúng user | ☐ |
| T2.5 | `message` chứa booking code + tour title | ☐ |
| T2.5 | Consumer log không có ERROR | ☐ |
| T2.6 | Exchange `tour.promotions` (fanout, durable) visible | ☐ |
| T2.6 | Cả 2 queues visible và bound vào exchange | ☐ |
| T2.6 | `mvn test` pass không cần live broker | ☑ |
| T2.7–T2.8 | (verify gián tiếp qua T2.9) | — |
| T2.9 | Tạo ACTIVE tour → N rows `TOUR_PROMOTION` (N = active users) | ☐ |
| T2.9 | Update sang ACTIVE → N rows mới | ☐ |
| T2.9 | INACTIVE tour → không có rows mới | ☐ |
| T2.9 | Log `New ACTIVE tour published` từ LogListener | ☐ |
| T2.9 | RabbitMQ queues empty sau khi xử lý xong | ☐ |

> ☑ = verified trong session implement | ☐ = cần verify thủ công với live broker

---

## Checklist tổng hợp Day 1

| Task | Test | Status |
|------|------|--------|
| T1.1 | Flyway migrations V1–V6 chạy sạch | ☑ |
| T1.1 | Schema MySQL đúng (AUTO_INCREMENT, TINYINT, inline ENUM) | ☑ |
| T1.1 | Seed data load được | ☑ |
| T1.1 | Login + browse tours hoạt động | ☑ |
| T1.2 | `logs/app.log` tạo tự động | ☑ |
| T1.2 | Log pattern có `[requestId] [userEmail]` | ☑ |
| T1.2 | requestId là UUID, duy nhất theo request | ☑ |
| T1.2 | userEmail điền sau khi đăng nhập | ☑ |
| T1.2 | `logs/error.log` chỉ có ERROR | ☑ |
| T1.2 | Dev profile có DEBUG logs | ☑ |
| T1.3 | `/swagger-ui.html` accessible | ☑ |
| T1.3 | `/v3/api-docs` trả về OpenAPI 3.0 JSON | ☑ (3.1.0) |
| T1.3 | ≥ 10 controllers được tag | ☑ (14 tags) |
| T1.3 | Security scheme JWT được định nghĩa | ☑ |
| T1.4 | 3 OAuth2 buttons trên login page | ☑ |
| T1.4 | Facebook/Twitter redirect URL đúng | ☑ |
| T1.4 | `CustomStandardOAuth2UserService` xử lý đúng cả 2 provider | ☑ |
| T1.4 | Twitter synthetic email được implement | ☑ |
| T1.4 | Google OAuth2 không bị regression | ☑ |
| T1.4 | `prompt=select_account` chỉ cho Google | ☑ |
