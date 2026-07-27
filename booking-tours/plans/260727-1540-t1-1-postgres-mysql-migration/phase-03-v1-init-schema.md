---
phase: 03
title: V1__init_schema.sql — Viết lại cho MySQL
status: pending
---

## Quy tắc chuyển đổi áp dụng

| PostgreSQL | MySQL |
|---|---|
| `BIGSERIAL PRIMARY KEY` | `BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY` |
| `BOOLEAN NOT NULL DEFAULT TRUE/FALSE` | `TINYINT(1) NOT NULL DEFAULT 1/0` |
| `NUMERIC(12,2)` | `DECIMAL(12,2)` |
| `NUMERIC(2,1)` | `DECIMAL(2,1)` |
| `TIMESTAMP NOT NULL DEFAULT NOW()` | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` |
| Inline `REFERENCES tbl(id)` | Explicit `CONSTRAINT fk_xxx FOREIGN KEY (col) REFERENCES tbl(id)` |
| *(end of CREATE TABLE)* | `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci` |

## Bảng cần xử lý (12 bảng)

1. `roles` — BIGSERIAL + ENGINE
2. `users` — BIGSERIAL + BOOLEAN→TINYINT + TIMESTAMP + FK (role_id)
3. `oauth_accounts` — BIGSERIAL + TIMESTAMP + FK (user_id CASCADE)
4. `user_bank_accounts` — BIGSERIAL + BOOLEAN + TIMESTAMP + FK
5. `categories` — BIGSERIAL + ENGINE
6. `tours` — BIGSERIAL + NUMERIC→DECIMAL + TIMESTAMP + FK (category_id)
7. `bookings` — BIGSERIAL + NUMERIC + TIMESTAMP + FK (user_id, tour_id)
8. `payments` — BIGSERIAL + NUMERIC + TIMESTAMP + FK
9. `reviews` — BIGSERIAL + TIMESTAMP + FK
10. `comments` — BIGSERIAL + BOOLEAN + TIMESTAMP + FK (parent_id self-ref)
11. `likes` — BIGSERIAL + TIMESTAMP + FK
12. `ratings` — BIGSERIAL + TIMESTAMP + FK + CHECK (MySQL 8.0+ hỗ trợ)

## Lưu ý tự-reference

`comments.parent_id` tham chiếu `comments(id)` — cần đặt FK sau khi bảng đã có PK.  
MySQL hỗ trợ self-referencing FK trong cùng CREATE TABLE statement.

## Todo
- [ ] Viết lại toàn bộ V1__init_schema.sql
- [ ] Verify không còn PostgreSQL syntax (BIGSERIAL, BOOLEAN, NUMERIC, REFERENCES inline)
- [ ] Mỗi bảng có ENGINE=InnoDB clause
- [ ] FK constraints đặt tên rõ ràng (fk_tablename_column)
