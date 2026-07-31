# E2E Testing Guide — SUN Booking Tours

> Test các phần đã hoàn thành theo `task-advanced-breakdown.md`.  
> Mỗi file day chứa: Prerequisites → Steps → Expected Result → Pass/Fail criteria.

## Files

| File | Nội dung |
|------|----------|
| [e2e-day-1-foundation.md](e2e-day-1-foundation.md) | T1.1 MySQL Migration · T1.2 Logback · T1.3 Swagger · T1.4 OAuth2 |
| [e2e-day-2-messaging.md](e2e-day-2-messaging.md) | T2.1 ActiveMQ · T2.2 Notifications · T2.3–T2.5 JMS Pipeline · T2.6–T2.9 RabbitMQ |

---

## Khởi động chung

```bash
# MySQL
mysql -u root -p'Aa@123456' -e "SHOW DATABASES LIKE 'booking_tours';"

# App (profile dev)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# RabbitMQ (chỉ cần cho Day 2 T2.6–T2.9)
brew services start rabbitmq
```

App sẵn sàng khi log xuất hiện: `Started BookingToursApplication in X.XXX seconds`

---

## Tổng hợp trạng thái

### Day 1

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

### Day 2

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

> ☑ = verified | ☐ = cần verify thủ công
