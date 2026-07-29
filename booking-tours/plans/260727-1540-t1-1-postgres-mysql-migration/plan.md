---
plan: T1.1 — PostgreSQL → MySQL Migration
spec_draft: plans/260727-1540-t1-1-postgres-mysql-migration/spec/t1-1-postgres-mysql-migration/
status: in_progress
created: 2026-07-27
---

# T1.1 — PostgreSQL → MySQL Migration

## Tổng quan

Chuyển đổi toàn bộ lớp database từ PostgreSQL sang MySQL 8.x.  
Đây là task nền tảng (không phụ thuộc task nào khác) cần hoàn thành trước sprint.

## Phases

| Phase | Mô tả | Trạng thái |
|---|---|---|
| [01 — pom.xml](phase-01-pom-dependencies.md) | Đổi driver + Flyway dependency | ⏳ |
| [02 — Properties](phase-02-properties.md) | Cập nhật datasource dev + prod | ⏳ |
| [03 — V1 Schema](phase-03-v1-init-schema.md) | Viết lại V1__init_schema.sql cho MySQL | ⏳ |
| [04 — Seed Files](phase-04-seed-files.md) | Fix V2–V5 cho MySQL syntax | ⏳ |

## Dependencies

Không có dependency ngoài. Tất cả task Day 1 đều phụ thuộc vào T1.1.

## Success Criteria

- `mvn spring-boot:run -Dspring-boot.run.profiles=dev` không lỗi Flyway
- 12 bảng tạo đúng trong MySQL
- Seed data load thành công
- Login + browse tours hoạt động end-to-end
