# E2E Testing — Day 3: WebSocket + Scheduler

> Quay lại: [E2E Testing Guide](e2e-testing-guide.md)

## Prerequisites

```bash
# MySQL + ActiveMQ đang chạy, app đã qua Day 2
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

App sẵn sàng khi log xuất hiện: `Apache ActiveMQ X.X.X (localhost, ...) started` và `Started BookingToursApplication in X.XXX seconds`

> **Scope:** T3.1–T3.7 — Real-time notifications (WebSocket/STOMP) và scheduled jobs

---

## Part 1: WebSocket / STOMP (T3.1–T3.4)

### 1.1 Verify STOMP connection

Mở browser DevTools → Console, truy cập bất kỳ trang nào khi đã đăng nhập.

**Expected:** Không có lỗi WebSocket trong console. SockJS connect thành công.

Kiểm tra nhanh trong Console:
```javascript
// Không có log này vì stompClient.debug = null, nhưng kiểm tra gián tiếp:
document.getElementById('notif-count')  // phải tồn tại nếu đã đăng nhập
```

---

### 1.2 Badge load khi page load

1. Đăng nhập với một user có notification chưa đọc (hoặc tạo thủ công qua DB)
2. Mở bất kỳ trang nào

**Expected:**
- Badge đỏ hiện số đúng trên bell icon nếu có notification chưa đọc
- Badge ẩn (`d-none`) nếu count = 0

Kiểm tra API trực tiếp:
```bash
curl -s http://localhost:8080/api/notifications/unread-count \
  -H "Cookie: JWT_TOKEN=<token>"
# {"count": N}
```

---

### 1.3 Real-time push khi admin confirm booking

**Setup:**
1. Mở 2 tab: tab 1 = trang user đang login, tab 2 = admin panel
2. Tạo một booking ở trạng thái PENDING

**Steps:**
1. Tab 1: Quan sát bell badge (ghi nhớ số hiện tại)
2. Tab 2: Admin → Bookings → Confirm booking vừa tạo
3. Tab 1: Quan sát ngay sau khi confirm (không reload)

**Expected:**
- Toast Bootstrap 5 xuất hiện góc màn hình với title "Booking Confirmed" (hoặc tương đương)
- Badge tăng thêm 1 mà không cần reload trang
- Row mới xuất hiện trong bảng `notifications` trên DB:

```sql
SELECT * FROM notifications ORDER BY created_at DESC LIMIT 1;
-- type = 'BOOKING_CONFIRMED', is_read = 0
```

---

### 1.4 Real-time push khi admin cancel booking

Lặp lại 1.3 với action Cancel thay vì Confirm.

**Expected:**
- Toast xuất hiện với nội dung cancel
- DB row có `type = 'BOOKING_CANCELLED'`

---

### 1.5 Notification list page

1. Click bell icon → trang `/profile/notifications`
2. Hoặc truy cập trực tiếp `http://localhost:8080/profile/notifications`

**Expected:**
- Danh sách notification hiển thị đúng thứ tự mới nhất trước
- Mỗi item có: title, message, thời gian tạo

Kiểm tra REST API:
```bash
curl -s "http://localhost:8080/api/notifications?page=0&size=10" \
  -H "Cookie: JWT_TOKEN=<token>"
# Page<NotificationDto> với content[], totalElements, totalPages
```

---

### 1.6 Mark all read

1. Vào trang `/profile/notifications` khi đang có badge > 0
2. Click bell icon trên navbar (không phải link thông thường)

**Expected:**
- POST `/api/notifications/mark-read` được gọi (kiểm tra Network tab)
- Badge biến mất sau khi trang redirect
- DB: `is_read = 1` cho tất cả notification của user

```sql
SELECT COUNT(*) FROM notifications WHERE user_id = <id> AND is_read = 0;
-- Kết quả: 0
```

---

### 1.7 WebSocket reconnect

1. Đang ở trang có WebSocket active
2. Tắt network tạm thời (DevTools → Network → Offline) trong 3 giây, bật lại

**Expected:**
- Sau ~5 giây, WebSocket reconnect tự động (do `setTimeout(connectWebSocket, 5000)` trong error handler)
- Không có lỗi JS exception uncaught

---

### 1.8 Unauthenticated user không kết nối WebSocket

1. Logout khỏi app
2. Truy cập trang public (ví dụ `/tours`)

**Expected:**
- `document.getElementById('notif-count')` trả về `null`
- Không có STOMP connect attempt (badge element null → `connectWebSocket()` không được gọi)
- Không có lỗi 403 WebSocket trong Network tab

---

## Part 2: Scheduler (T3.5–T3.6)

> Jobs chạy theo cron thực (`0 30 0 * * *` / `0 0 1 * * *`) — test thủ công bằng cách trigger qua DB setup + cron expression tạm thời, hoặc gọi method trực tiếp qua Spring Actuator.

---

### 2.1 Setup test data cho AutoCompleteBookingJob

Tạo booking CONFIRMED với tour đã qua ngày khởi hành:

```sql
-- Tìm một tour để lấy id
SELECT id, title, departure_date FROM tours LIMIT 5;

-- Tạo booking CONFIRMED với departure_date trong quá khứ
-- (hoặc UPDATE tour.departure_date sang quá khứ)
UPDATE tours SET departure_date = '2024-01-01' WHERE id = <tour_id>;

-- Verify booking CONFIRMED tồn tại cho tour này
SELECT b.id, b.status, t.departure_date
FROM bookings b JOIN tours t ON b.tour_id = t.id
WHERE b.status = 'CONFIRMED' AND t.departure_date < CURDATE();
```

---

### 2.2 Trigger AutoCompleteBookingJob

**Cách 1 — Đổi cron tạm thời** (nhanh nhất):

Trong `AutoCompleteBookingJob.java`, đổi cron thành `fixedDelay` để chạy ngay:
```java
@Scheduled(fixedDelay = 10000)  // chạy sau 10s khi app start
```
Restart app, đợi 10 giây, kiểm tra kết quả, đổi lại cron gốc.

**Cách 2 — Spring Actuator** (nếu đã enable):
```bash
curl -X POST http://localhost:8080/actuator/scheduledtasks
```

---

### 2.3 Verify AutoCompleteBookingJob

Sau khi job chạy:

```sql
-- Booking phải chuyển sang COMPLETED
SELECT id, status FROM bookings WHERE id = <booking_id>;
-- status = 'COMPLETED'

-- Job log phải được ghi
SELECT * FROM scheduled_job_logs
WHERE job_name = 'AutoCompleteBookingJob'
ORDER BY executed_at DESC LIMIT 1;
-- status = 'SUCCESS', records_processed = N, duration_ms > 0
```

Kiểm tra log file:
```bash
grep "AutoCompleteBookingJob" logs/app.log | tail -5
# [AutoCompleteBookingJob] Completed X bookings in Xms
```

---

### 2.4 Setup test data cho PendingPaymentCleanupJob

```sql
-- Tạo booking PENDING với createdAt > 48h trước và không có payment
INSERT INTO bookings (booking_code, user_id, tour_id, status, num_participants, total_price, created_at)
VALUES ('BK-TEST-CLEANUP', <user_id>, <tour_id>, 'PENDING', 1, 1000000, NOW() - INTERVAL 49 HOUR);

-- Verify không có payment cho booking này
SELECT * FROM payments WHERE booking_id = LAST_INSERT_ID();
-- Empty set
```

---

### 2.5 Verify PendingPaymentCleanupJob

Sau khi job chạy (trigger tương tự 2.2):

```sql
-- Booking phải chuyển sang CANCELLED
SELECT id, status FROM bookings WHERE booking_code = 'BK-TEST-CLEANUP';
-- status = 'CANCELLED'

-- Job log
SELECT * FROM scheduled_job_logs
WHERE job_name = 'PendingPaymentCleanupJob'
ORDER BY executed_at DESC LIMIT 1;
-- status = 'SUCCESS', records_processed >= 1
```

Log file:
```bash
grep "PendingPaymentCleanupJob" logs/app.log | tail -5
# [PendingPaymentCleanupJob] Cancelled X stale bookings in Xms
```

---

### 2.6 Job fail scenario

Simulate lỗi bằng cách stop MySQL tạm thời khi job đang chạy (hoặc revoke quyền DB).

**Expected:**
- Log ERROR xuất hiện trong `logs/error.log`:
  ```
  [PendingPaymentCleanupJob] Failed after Xms: ...
  ```
- Row trong `scheduled_job_logs` có `status = 'FAILED'`, `error_message` được điền

---

## Part 3: @Async (T3.7)

### 3.1 Verify notification không block HTTP response

1. Bật debug logging tạm thời trong `application-dev.properties`:
   ```properties
   logging.level.com.sunasterisk=DEBUG
   ```
2. Admin confirm một booking
3. Kiểm tra log

**Expected trong log:**
- HTTP response của confirm action hoàn tất trước
- Thread `notif-async-1` (hoặc `notif-async-2`, `notif-async-3`) xử lý notification sau
- Hai log line khác thread:
  ```
  [http-nio-8080-exec-X] ... adminConfirmBooking completed
  [notif-async-X] ... saveNotification called for userId=...
  ```

---

## Checklist tổng kết Day 3

| # | Test case | Pass |
|---|-----------|------|
| 1.2 | Badge load đúng khi page load | ☐ |
| 1.3 | Toast + badge tăng khi admin confirm booking | ☐ |
| 1.4 | Toast + badge tăng khi admin cancel booking | ☐ |
| 1.5 | `/profile/notifications` hiển thị đúng danh sách | ☐ |
| 1.6 | Mark all read → badge về 0, DB cập nhật | ☐ |
| 1.7 | WebSocket tự reconnect sau mất kết nối | ☐ |
| 1.8 | User chưa đăng nhập không trigger WebSocket connect | ☐ |
| 2.3 | AutoCompleteBookingJob chuyển CONFIRMED → COMPLETED | ☐ |
| 2.3 | Job log SUCCESS được ghi vào `scheduled_job_logs` | ☐ |
| 2.5 | PendingPaymentCleanupJob chuyển PENDING → CANCELLED | ☐ |
| 2.5 | Job log SUCCESS được ghi vào `scheduled_job_logs` | ☐ |
| 2.6 | Job fail → log FAILED trong DB và `error.log` | ☐ |
| 3.1 | Notification save chạy trên thread `notif-async-X` | ☐ |
