---
feature_id: F001
slug: t1-1-postgres-mysql-migration
lang: vi
status: draft
spec_draft: true
---

# F001 — Chuyển đổi Database: PostgreSQL → MySQL

## Mục tiêu

Thay thế toàn bộ lớp database từ PostgreSQL sang MySQL 8.x. Đây là bước nền tảng bắt buộc trước tất cả các task còn lại trong sprint.

## Phạm vi thay đổi

| Layer | File | Loại thay đổi |
|---|---|---|
| Build | `pom.xml` | Đổi driver + Flyway plugin |
| Config | `application-dev.properties` | Cập nhật datasource + dialect |
| Config | `application-prod.properties` | Cập nhật datasource + dialect |
| Migration | `V1__init_schema.sql` | Viết lại hoàn toàn cho MySQL |
| Migration | `V2__seed_data.sql` | Đổi `ON CONFLICT` → `INSERT IGNORE` |
| Migration | `V3__seed_tours.sql` | Đổi `ON CONFLICT` → `INSERT IGNORE` |
| Migration | `V4__seed_reviews.sql` | Đổi `ON CONFLICT` → `INSERT IGNORE` |
| Migration | `V5__unique_payment_per_booking.sql` | Đổi DELETE USING → MySQL syntax |

## Quy tắc chuyển đổi DDL

| PostgreSQL | MySQL |
|---|---|
| `BIGSERIAL PRIMARY KEY` | `BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY` |
| `BOOLEAN` / `DEFAULT TRUE/FALSE` | `TINYINT(1)` / `DEFAULT 1/0` |
| `NUMERIC(p,s)` | `DECIMAL(p,s)` |
| `TIMESTAMP` | `DATETIME(6)` |
| `DEFAULT NOW()` | `DEFAULT CURRENT_TIMESTAMP(6)` |
| Inline `REFERENCES tbl(id)` | Explicit `CONSTRAINT fk_xxx FOREIGN KEY ... REFERENCES ...` |
| `ON CONFLICT ... DO NOTHING` | `INSERT IGNORE INTO` |
| `DELETE FROM p USING p2 WHERE ...` | `DELETE p FROM payments p INNER JOIN payments p2 ON ...` |
| *(cuối mỗi bảng)* | `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci` |

## Dependency

- `mysql-connector-j` (runtime) thay `postgresql`
- `flyway-mysql` thay `flyway-database-postgresql`

## Tiêu chí chấp nhận

1. `mvn spring-boot:run -Dspring-boot.run.profiles=dev` khởi động không lỗi Flyway
2. Tất cả 12 bảng được tạo trong MySQL với schema đúng
3. Seed data (V2–V4) nạp thành công
4. Đăng nhập và duyệt tour hoạt động end-to-end

## Nằm ngoài phạm vi

- Entity class Java (không thay đổi — Hibernate tự điều chỉnh)
- Repository / Service / Controller (không thay đổi)
- Data migration thực (chỉ schema + seed mới)
