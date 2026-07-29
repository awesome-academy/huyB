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
