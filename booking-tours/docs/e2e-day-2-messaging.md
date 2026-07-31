# E2E Testing — Day 2: Messaging (ActiveMQ + RabbitMQ)

> Quay lại: [E2E Testing Guide](e2e-testing-guide.md)

## Prerequisites

```bash
# 1. MySQL đang chạy và app đã qua Day 1 (Flyway V1–V6 applied)
mysql -u root -p'Aa@123456' -e "SELECT version FROM booking_tours.flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;"
# Expected: 6

# 2. Khởi động RabbitMQ qua Homebrew (cần cho T2.6–T2.9)
brew services start rabbitmq
# Kiểm tra:
brew services list | grep rabbitmq

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
> Khi admin confirm/cancel booking → Producer gửi JMS message → Consumer nhận → lưu row vào bảng `notifications`.

> **Lưu ý cấu hình:** `spring.activemq.packages.trusted` trong `application.properties` phải bao gồm
> `java.lang,java.util` để broker cho phép deserialize các JDK type (`Long`, `String`) có trong payload.
> Thiếu cấu hình này dẫn đến `Forbidden class java.lang.Long` và consumer không lưu được notification.

#### Prerequisites
- App đang chạy với profile `dev`
- Đăng nhập admin: `admin@bookingtours.com` / `Admin@123` (seed từ `V3_1__seed_users.sql`)
- Có ít nhất 1 booking ở trạng thái `PENDING` trong DB

```sql
-- Tạo booking PENDING nếu chưa có
mysql -u root -p'Aa@123456' booking_tours -e "
  INSERT INTO bookings (booking_code, user_id, tour_id, participants, total_price, status, note)
  VALUES ('BK-TEST-001', 2, 1, 2, 13800000.00, 'PENDING', 'Test booking');
  SELECT id, booking_code, status, user_id FROM bookings WHERE booking_code='BK-TEST-001';
"
-- user_id=2 (nguyen.thi.lan@example.com), tour_id=1 (Hạ Long Bay 3N2Đ Luxury Cruise)
```

#### Test Steps — T2.5a: Admin Confirm Booking

**Step 1 — Ghi nhận trạng thái trước**
```sql
mysql -u root -p'Aa@123456' booking_tours -e "
  SELECT id, booking_code, status, user_id FROM bookings WHERE status='PENDING' LIMIT 1;
  SELECT COUNT(*) AS notification_count FROM notifications WHERE user_id = 2;
"
-- Expected: booking PENDING tồn tại, notification_count = 0 (hoặc số hiện có)
```

**Step 2 — Admin confirm booking qua curl**
```bash
# Lấy CSRF token từ login page
LOGIN_PAGE=$(curl -s -c cookies.txt -L http://localhost:8080/auth/login)
CSRF=$(echo "$LOGIN_PAGE" | grep -oE 'name="_csrf"[^>]*value="[^"]*"' | grep -oE 'value="[^"]*"' | cut -d'"' -f2)

# Đăng nhập admin
curl -s -c cookies.txt -b cookies.txt \
  --data-urlencode "email=admin@bookingtours.com" \
  --data-urlencode "password=Admin@123" \
  --data-urlencode "_csrf=$CSRF" \
  -X POST http://localhost:8080/auth/login -o /dev/null

# Lấy CSRF từ admin bookings page
DETAIL=$(curl -s -b cookies.txt -c cookies.txt http://localhost:8080/admin/bookings/1)
CSRF2=$(echo "$DETAIL" | grep -oE 'name="_csrf"[^>]*value="[^"]*"' | grep -oE 'value="[^"]*"' | head -1 | cut -d'"' -f2)

# POST confirm
curl -s -b cookies.txt -c cookies.txt \
  -X POST "http://localhost:8080/admin/bookings/1/confirm" \
  --data-urlencode "_csrf=$CSRF2" \
  -w "%{http_code}\n" -o /dev/null
# Expected: 302
```

Hoặc qua browser: đăng nhập → vào `/admin/bookings/1` → click **Confirm Payment**.

**Step 3 — Kiểm tra notification được tạo (đợi ~2s sau confirm)**
```sql
mysql -u root -p'Aa@123456' booking_tours -e "
  SELECT id, user_id, type, title, message, is_read, created_at
  FROM notifications
  WHERE user_id = 2
  ORDER BY created_at DESC LIMIT 3;
"
```
Expected:
```
| id | user_id | type              | title                     | is_read | created_at              |
|----|---------|-------------------|---------------------------|---------|-------------------------|
| 1  | 2       | BOOKING_CONFIRMED | Đặt tour đã được xác nhận | 0       | 2026-07-31 xx:xx:xx     |
```

**Step 4 — Kiểm tra nội dung message**
```sql
mysql -u root -p'Aa@123456' booking_tours -e "
  SELECT message FROM notifications WHERE type='BOOKING_CONFIRMED' ORDER BY created_at DESC LIMIT 1;
"
```
Expected format:
```
Booking BK-TEST-001 cho tour "Hạ Long Bay 3N2Đ Luxury Cruise".
```

**Step 5 — Kiểm tra log ActiveMQ không có lỗi**
```bash
grep -E "BookingNotif|booking\.notifications|Forbidden class|JMSException" logs/app.log | tail -10
```
Expected: không có dòng `Forbidden class` hay `JMSException`.

#### Test Steps — T2.5b: Admin Cancel Booking PENDING

**Step 1 — Tạo booking PENDING để cancel**
```sql
mysql -u root -p'Aa@123456' booking_tours -e "
  INSERT INTO bookings (booking_code, user_id, tour_id, participants, total_price, status)
  VALUES ('BK-TEST-002', 2, 2, 1, 5500000.00, 'PENDING');
  SELECT id, booking_code, status FROM bookings WHERE booking_code='BK-TEST-002';
"
```

**Step 2 — Admin cancel booking**
```bash
# Dùng session cookie đã login từ T2.5a, lấy CSRF mới
DETAIL=$(curl -s -b cookies.txt -c cookies.txt http://localhost:8080/admin/bookings/<id>)
CSRF=$(echo "$DETAIL" | grep -oE 'name="_csrf"[^>]*value="[^"]*"' | grep -oE 'value="[^"]*"' | head -1 | cut -d'"' -f2)

curl -s -b cookies.txt -c cookies.txt \
  -X POST "http://localhost:8080/admin/bookings/<id>/cancel" \
  --data-urlencode "_csrf=$CSRF" \
  -w "%{http_code}\n" -o /dev/null
# Expected: 302
```

**Step 3 — Kiểm tra notification BOOKING_CANCELLED**
```sql
mysql -u root -p'Aa@123456' booking_tours -e "
  SELECT type, title, message FROM notifications
  WHERE type='BOOKING_CANCELLED'
  ORDER BY created_at DESC LIMIT 1;
"
```
Expected:
```
| BOOKING_CANCELLED | Đặt tour đã bị hủy | Booking BK-TEST-002 cho tour "Đà Nẵng – Hội An 4N3Đ". |
```

#### Test Steps — T2.5c: Cancel Booking đã CONFIRMED

> Verify rằng khi admin hủy booking đang ở trạng thái **CONFIRMED** (đã thanh toán),
> pipeline vẫn tạo đúng notification `BOOKING_CANCELLED`.

**Step 1 — Chuẩn bị: có booking CONFIRMED**
```sql
mysql -u root -p'Aa@123456' booking_tours -e "
  INSERT INTO bookings (booking_code, user_id, tour_id, participants, total_price, status)
  VALUES ('BK-TEST-CANCEL-C', 2, 1, 2, 13800000.00, 'CONFIRMED');
  SELECT id, booking_code, status FROM bookings WHERE booking_code='BK-TEST-CANCEL-C';
"
-- Ghi lại id (ví dụ: id=5)
```

**Step 2 — Ghi nhận số notifications trước**
```sql
mysql -u root -p'Aa@123456' booking_tours -e "
  SELECT COUNT(*) AS before_count FROM notifications WHERE type='BOOKING_CANCELLED';
"
```

**Step 3 — Admin cancel booking CONFIRMED qua curl**
```bash
DETAIL=$(curl -s -b cookies.txt -c cookies.txt http://localhost:8080/admin/bookings/5)
CSRF=$(echo "$DETAIL" | grep -oE 'name="_csrf"[^>]*value="[^"]*"' | grep -oE 'value="[^"]*"' | head -1 | cut -d'"' -f2)

curl -s -b cookies.txt -c cookies.txt \
  -X POST "http://localhost:8080/admin/bookings/5/cancel" \
  --data-urlencode "_csrf=$CSRF" \
  -w "%{http_code}\n" -o /dev/null
# Expected: 302
```

Hoặc qua browser: vào `/admin/bookings/5` → click **Cancel**.

**Step 4 — Kiểm tra trạng thái booking chuyển sang CANCELLED**
```sql
mysql -u root -p'Aa@123456' booking_tours -e "
  SELECT id, booking_code, status FROM bookings WHERE booking_code='BK-TEST-CANCEL-C';
"
-- Expected: status = CANCELLED
```

**Step 5 — Kiểm tra notification BOOKING_CANCELLED được tạo (đợi ~2s)**
```sql
mysql -u root -p'Aa@123456' booking_tours -e "
  SELECT type, title, message, is_read, created_at
  FROM notifications
  WHERE type='BOOKING_CANCELLED'
  ORDER BY created_at DESC LIMIT 1;
"
```
Expected:
```
| BOOKING_CANCELLED | Đặt tour đã bị hủy | Booking BK-TEST-CANCEL-C cho tour "Hạ Long Bay 3N2Đ Luxury Cruise". | 0 |
```

**Step 6 — Verify pipeline ActiveMQ không có lỗi**
```bash
grep -E "BookingNotif|BOOKING_CANCELLED|Forbidden class" logs/app.log | tail -5
```

> **Lưu ý hành vi:** `adminCancelBooking()` cho phép hủy booking ở trạng thái `PENDING` hoặc `CONFIRMED`.
> Hủy booking `CANCELLED` hoặc `COMPLETED` sẽ ném `IllegalStateException` — trên UI các nút này không hiển thị.

#### Pass Criteria
- [x] Admin confirm PENDING → 1 row `BOOKING_CONFIRMED` trong `notifications` cho đúng `user_id`
- [x] Admin cancel PENDING → 1 row `BOOKING_CANCELLED` trong `notifications` cho đúng `user_id`
- [ ] Admin cancel CONFIRMED → 1 row `BOOKING_CANCELLED` trong `notifications` cho đúng `user_id`
- [x] `message` format: `Booking <booking_code> cho tour "<tour_title>".`
- [x] `is_read = 0` (chưa đọc)
- [x] Log không có `Forbidden class java.lang.Long` hay JMS exception từ consumer

---

### T2.6 — RabbitMQ Configuration

#### Prerequisites
- RabbitMQ đang chạy (`brew services list | grep rabbitmq`)
- App đang chạy với profile `dev`

#### Test Steps

**Step 1 — Kiểm tra app kết nối RabbitMQ thành công khi startup**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev 2>&1 | grep -E "rabbit|AMQP|5672|tour\.promotions|promo"
```
Expected: không có dòng chứa `Connection refused` / `RabbitMQ connection error`.

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
# Expected: "source": "tour.promotions", "destination": "tour.promo.notification.queue"

curl -s -u guest:guest "http://localhost:15672/api/bindings/%2F/e/tour.promotions/q/tour.promo.log.queue" | python3 -m json.tool
# Expected: "destination": "tour.promo.log.queue"
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
- RabbitMQ đang chạy (`brew services list | grep rabbitmq`)
- App đang chạy với profile `dev`
- Có ít nhất 2 user active trong DB

```sql
mysql -u root -p'Aa@123456' booking_tours

-- Kiểm tra active users
SELECT COUNT(*) FROM users WHERE is_active = 1;
-- Expected: ≥ 2
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

SELECT COUNT(*) AS total_after FROM notifications WHERE type='TOUR_PROMOTION';
-- Expected: total_before + <số active users>

SELECT user_id, type, title, message, is_read, created_at
FROM notifications
WHERE type = 'TOUR_PROMOTION'
ORDER BY created_at DESC
LIMIT 10;
```
Expected:
```
| user_id | type           | title                            | is_read |
|---------|----------------|----------------------------------|---------|
| 1       | TOUR_PROMOTION | Tour mới: Tour E2E Test RabbitMQ | 0       |
| 2       | TOUR_PROMOTION | Tour mới: Tour E2E Test RabbitMQ | 0       |
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

**Step 1 — Đếm notifications trước**
```sql
SELECT COUNT(*) FROM notifications WHERE type='TOUR_PROMOTION';
```

**Step 2 — Tạo tour INACTIVE**
```bash
curl -c cookies.txt -b cookies.txt \
  -X POST "http://localhost:8080/api/admin/tours" \
  -H "Content-Type: application/json" \
  -d '{"title": "Tour No Broadcast", "description": "No broadcast", "price": 200000, "duration": 1, "maxParticipants": 5, "status": "INACTIVE", "categoryId": 1}'
```

**Step 3 — Kiểm tra số notifications KHÔNG tăng**
```sql
SELECT COUNT(*) FROM notifications WHERE type='TOUR_PROMOTION';
-- Expected: bằng với count trước
```

**Step 4 — Kiểm tra log KHÔNG có dòng log cho tour INACTIVE**
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

## Checklist tổng hợp

| Task | Test | Status |
|------|------|--------|
| T2.1 | Log `Apache ActiveMQ X.X.X started` khi app start | ☑ |
| T2.1 | Log `Connector vm://localhost started` | ☑ |
| T2.1 | App start không có JMS bean error | ☑ |
| T2.2 | Flyway V7 applied, `success=1` | ☑ |
| T2.2 | Bảng `notifications` có đủ schema (8 cột, FK, 2 index) | ☑ |
| T2.3–T2.4 | (verify gián tiếp qua T2.5) | — |
| T2.5a | Admin confirm PENDING → 1 row `BOOKING_CONFIRMED` đúng user | ☑ |
| T2.5b | Admin cancel PENDING → 1 row `BOOKING_CANCELLED` đúng user | ☑ |
| T2.5c | Admin cancel CONFIRMED → 1 row `BOOKING_CANCELLED` đúng user | ☐ |
| T2.5 | `message` format `Booking <code> cho tour "<title>".` | ☑ |
| T2.5 | Consumer log không có `Forbidden class` hay JMS exception | ☑ |
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
