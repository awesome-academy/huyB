# Phase 02 — Notification Entity + Flyway V7 + Repository + DTO (T2.2)

## Context links
- Spec: `../spec/t2-messaging.md`
- Plan: `plan.md`
- Style refs: `src/main/resources/db/migration/V1__init_schema.sql`, `entity/Booking.java`, `entity/BaseEntity.java`

## Overview
- **Priority:** P2
- **Status:** completed
- **Depends on:** none (parallel with 01, 05)
- Persistence layer for notifications: new table (V7), entity + enum, repository queries, and a read DTO. Foundation shared by both ActiveMQ and RabbitMQ paths.

## Key insights
- **V7** is the next free version (V1–V6 exist; V6 = seed_admin_user). Using V6 would collide.
- Entities extend `BaseEntity` (auto `created_at`/`updated_at` via JPA auditing). Table columns must therefore include `created_at`/`updated_at` `DATETIME(6)` with defaults — match existing style.
- MySQL 8, InnoDB, utf8mb4. FK to `users(id)`.
- Enum stored as `VARCHAR` via `@Enumerated(EnumType.STRING)` (project convention — see `Role`, `TourStatus`).
- Repository methods needed: paged list by user (newest first), unread count.

## Requirements
**Functional**
- Table `notifications`: id, user_id (FK), type, title, message, is_read, created_at, updated_at.
- Entity `Notification` + inner enum `NotificationType { BOOKING_CONFIRMED, BOOKING_CANCELLED, PAYMENT_CONFIRMED, TOUR_PROMOTION, SYSTEM }`.
- Repo: `findByUserIdOrderByCreatedAtDesc(Long, Pageable)`, `countByUserIdAndIsReadFalse(Long)`.
- DTO `NotificationDto` for read model.

**Non-functional**
- Migration idempotent (`CREATE TABLE IF NOT EXISTS`) matching V1 style.
- Index on `user_id` for the list/count queries.

## Architecture / data flow
Write path (later phases): consumers → repository `save`/`saveAll` → rows.
Read path (Day 3, out of scope now): repository → `NotificationDto`.

## Related code files
**Create**
- `src/main/resources/db/migration/V7__create_notifications_table.sql`
- `src/main/java/com/sunasterisk/bookingtours/entity/Notification.java`
- `src/main/java/com/sunasterisk/bookingtours/repository/NotificationRepository.java`
- `src/main/java/com/sunasterisk/bookingtours/dto/NotificationDto.java`

## Implementation steps
1. Write `V7__create_notifications_table.sql` (MySQL, InnoDB, utf8mb4). Columns:
   - `id BIGINT AUTO_INCREMENT PRIMARY KEY`
   - `user_id BIGINT NOT NULL`
   - `type VARCHAR(30) NOT NULL` (holds enum name)
   - `title VARCHAR(255) NOT NULL`
   - `message TEXT NOT NULL`
   - `is_read TINYINT(1) NOT NULL DEFAULT 0`
   - `created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)`
   - `updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)`
   - `CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id)`
   - `KEY idx_notifications_user (user_id)` (or composite `(user_id, is_read)` to serve the unread count).
2. Create `entity/Notification.java`:
   - Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`, `@Entity @Table(name = "notifications")`, extends `BaseEntity`.
   - Fields: `@Id @GeneratedValue(IDENTITY) Long id`; `@Column(name="user_id") Long userId` (store scalar FK, NOT a `@ManyToOne` — keeps batch insert cheap and consumers decoupled from User loading); `@Enumerated(EnumType.STRING) @Column(length=30) NotificationType type`; `String title`; `@Column(columnDefinition="TEXT") String message`; `@Column(name="is_read") Boolean isRead` (default `false` via `@Builder.Default`).
   - Inner `public enum NotificationType { BOOKING_CONFIRMED, BOOKING_CANCELLED, PAYMENT_CONFIRMED, TOUR_PROMOTION, SYSTEM }`.
3. Create `repository/NotificationRepository.java` extends `JpaRepository<Notification, Long>`:
   - `Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);`
   - `long countByUserIdAndIsReadFalse(Long userId);`
4. Create `dto/NotificationDto.java` (Lombok `@Getter @Setter @Builder`): `id, type (String or enum), title, message, isRead, createdAt`. Add a static `from(Notification)` mapper or leave mapping to the service (choose service-side to keep DTO dumb — DRY with existing DTO style which are plain data holders).
5. Run app in `dev`; confirm Flyway applies V7 (log line + `flyway_schema_history` row).
6. `mvn compile` clean.

## Todo
- [x] Write `V7__create_notifications_table.sql` matching V1 style
- [x] Create `Notification` entity + inner `NotificationType` enum
- [x] Use scalar `userId` (not `@ManyToOne`)
- [x] Create `NotificationRepository` with the two query methods
- [x] Create `NotificationDto`
- [x] Flyway applies V7 successfully
- [x] `mvn compile` clean

## Success criteria
- Flyway V7 applies; `notifications` table exists with correct columns/FK/index.
- `Notification` persistable and queryable via repository.
- `mvn compile` passes.

## Risk assessment
| Risk | L | I | Mitigation |
|------|---|---|------------|
| Version collision / out-of-order | Low | High | Confirmed V7 is next free; `out-of-order=false` already set |
| `is_read` boolean ↔ TINYINT(1) mapping | Low | Med | Matches existing `is_active` pattern in `users` |
| Batch insert perf with `@ManyToOne` user | Med | Med | Store scalar `userId` — avoids per-row User fetch |

## Security considerations
- FK enforces referential integrity; no user-supplied SQL. Message text comes from server-side templates, not raw user input.

## Rollback
- Flyway Community has no `undo`. Manual rollback: `DROP TABLE notifications;` then delete the V7 history row. Delete the 3 Java files.

## Next steps
- Unblocks phase 03 (NotificationService uses repository) and phase 06 (broadcast uses repository).
