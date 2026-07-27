---
doc_type: system-forward-draft
promotes_to: docs/system/architecture.md
status: draft
---

# Kiến trúc hệ thống — SUN Booking Tours

## Stack công nghệ

| Layer | Công nghệ |
|---|---|
| Web framework | Spring Boot 4.0.6 · Java 21 |
| Security | Spring Security 6 · JWT (cookie) · OAuth2 (Google) |
| View | Thymeleaf 3 |
| Persistence | Spring Data JPA · Hibernate 6 |
| **Database** | **MySQL 8.x** (đã chuyển từ PostgreSQL) |
| Migration | Flyway |
| Build | Maven |

## Lớp persistence

- **Driver:** `com.mysql.cj.jdbc.Driver` (mysql-connector-j)
- **Dialect:** `org.hibernate.dialect.MySQLDialect`
- **Storage engine:** InnoDB (utf8mb4 / utf8mb4_unicode_ci)
- **Connection pool:** HikariCP (mặc định Spring Boot)
- **Migration tool:** Flyway với plugin `flyway-mysql`

## Schema overview (12 bảng)

```
roles ← users ← oauth_accounts
              ← user_bank_accounts
              ← bookings ← payments (→ user_bank_accounts)
              ← reviews ← comments
                        ← likes
categories ← tours ← bookings
                   ← ratings
```

## Quy ước DDL (MySQL)

- Primary key: `BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY`
- Boolean flags: `TINYINT(1)` với `DEFAULT 0/1`
- Tiền tệ: `DECIMAL(12,2)`
- Timestamp: `DATETIME(6)` · `DEFAULT CURRENT_TIMESTAMP(6)`
- Toàn bộ bảng: `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`
- Foreign key: khai báo tường minh bằng `CONSTRAINT fk_xxx FOREIGN KEY`
