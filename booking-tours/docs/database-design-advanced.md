# Database Design — Advanced (MySQL 8.0+)

**Project:** SUN Booking Tours  
**Database Engine:** MySQL 8.0+  
**Charset:** utf8mb4 / Collation: utf8mb4_unicode_ci  
**Migration Tool:** Flyway (versioned SQL scripts)  
**Total Tables:** 16 (12 existing + 4 new for advanced features)

---

## 1. Overview

### Database Summary

| Category | Tables |
|---|---|
| Auth & Identity | `roles`, `users`, `oauth_accounts` |
| Tour Domain | `categories`, `tours` |
| Booking Domain | `bookings`, `user_bank_accounts`, `payments` |
| Social Domain | `reviews`, `comments`, `likes`, `ratings` |
| Notification (NEW) | `notifications` |
| Operations (NEW) | `scheduled_job_logs`, `tour_import_jobs` |
| SOAP Service (NEW) | `currency_rates` |

### Relationship Summary

```
roles           1──N  users
users           1──N  oauth_accounts
users           1──N  bookings
users           1──N  user_bank_accounts
users           1──N  reviews
users           1──N  comments
users           M──N  likes         (via likes table)
users           M──N  ratings       (via ratings table)
users           1──N  notifications
users           1──N  tour_import_jobs
categories      1──N  tours         (SET NULL on delete)
tours           1──N  bookings
tours           M──N  users         (via ratings)
bookings        1──1  payments
user_bank_accounts 1──N payments
reviews         1──N  comments
reviews         1──N  likes
comments        1──N  comments      (self-ref, 1 level deep)
```

---

## 2. PostgreSQL → MySQL Migration Considerations

| Aspect | PostgreSQL | MySQL 8.0+ |
|---|---|---|
| Auto-increment | `BIGSERIAL` / `SERIAL` | `BIGINT NOT NULL AUTO_INCREMENT` |
| Boolean | `BOOLEAN` (true/false) | `TINYINT(1)` (1/0) |
| ENUM | `CREATE TYPE ... AS ENUM(...)` | Inline `ENUM('A','B')` |
| Text | `TEXT` (unlimited) | `TEXT` (64KB) / `MEDIUMTEXT` (16MB) |
| JSON | `JSONB` | `JSON` / `TEXT` |
| String collation | `UTF8` default | `utf8mb4_unicode_ci` (supports emojis) |
| DEFERRABLE FK | Supported | **Not supported** — remove all `DEFERRABLE INITIALLY DEFERRED` |
| CHECK constraints | Full support | Supported in MySQL 8.0.16+ |
| UNIQUE index naming | Auto-named | Explicit `UNIQUE KEY uk_name (col)` |
| FK index | Auto-created | Must add `INDEX` explicitly if no UNIQUE covers it |
| Schema separation | Schemas | Databases (use single DB `booking_tours`) |
| NOW() / CURRENT_TIMESTAMP | Both valid | `NOW()` / `CURRENT_TIMESTAMP` both valid |
| Flyway dialect | `spring.flyway.locations=classpath:db/migration/postgresql` | `spring.flyway.locations=classpath:db/migration/mysql` |

**Key action:** Create a new migration directory `db/migration/mysql/` and rewrite V1–V5 as MySQL-compatible SQL. Flyway will handle versioned execution.

---

## 3. Complete MySQL DDL — All 16 Tables

### 3.1 Existing Tables (MySQL-Migrated)

```sql
-- ============================================================
-- V1: Core schema
-- ============================================================

CREATE TABLE roles (
    id   BIGINT       NOT NULL AUTO_INCREMENT,
    name VARCHAR(50)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------

CREATE TABLE users (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    email       VARCHAR(255)    NOT NULL,
    password    VARCHAR(255)    NULL,                          -- NULL for pure OAuth users
    full_name   VARCHAR(255)    NOT NULL,
    phone       VARCHAR(20)     NULL,
    avatar_url  VARCHAR(1000)   NULL,
    is_active   TINYINT(1)      NOT NULL DEFAULT 1,
    role_id     BIGINT          NOT NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    INDEX idx_users_role_id (role_id),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------

CREATE TABLE oauth_accounts (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    provider         ENUM('GOOGLE','FACEBOOK','TWITTER') NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_oauth_provider_uid (provider, provider_user_id),
    INDEX idx_oauth_user_id (user_id),
    CONSTRAINT fk_oauth_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------

CREATE TABLE categories (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    description TEXT         NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_categories_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------

CREATE TABLE tours (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    title               VARCHAR(255)    NOT NULL,
    description         MEDIUMTEXT      NULL,
    price               DECIMAL(12,2)   NOT NULL,
    duration_days       INT             NOT NULL,
    max_participants    INT             NOT NULL,
    departure_location  VARCHAR(255)    NOT NULL,
    destination         VARCHAR(255)    NOT NULL,
    departure_date      DATE            NOT NULL,
    thumbnail_url       VARCHAR(1000)   NULL,
    status              ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    avg_rating          DECIMAL(3,2)    NULL DEFAULT NULL,
    category_id         BIGINT          NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_tours_status (status),
    INDEX idx_tours_category_id (category_id),
    INDEX idx_tours_departure_date (departure_date),
    FULLTEXT INDEX ft_tours_search (title, destination, departure_location),
    CONSTRAINT fk_tours_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------

CREATE TABLE bookings (
    id            BIGINT          NOT NULL AUTO_INCREMENT,
    booking_code  VARCHAR(30)     NOT NULL,
    user_id       BIGINT          NOT NULL,
    tour_id       BIGINT          NOT NULL,
    participants  INT             NOT NULL,
    total_price   DECIMAL(12,2)   NOT NULL,
    status        ENUM('PENDING','CONFIRMED','CANCELLED','COMPLETED') NOT NULL DEFAULT 'PENDING',
    note          TEXT            NULL,
    created_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_bookings_code (booking_code),
    INDEX idx_bookings_user_id (user_id),
    INDEX idx_bookings_tour_id (tour_id),
    INDEX idx_bookings_status (status),
    INDEX idx_bookings_created_at (created_at),
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_bookings_tour FOREIGN KEY (tour_id) REFERENCES tours (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------

CREATE TABLE user_bank_accounts (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    bank_name      VARCHAR(100) NOT NULL,
    account_number VARCHAR(50)  NOT NULL,
    account_holder VARCHAR(255) NOT NULL,
    is_default     TINYINT(1)   NOT NULL DEFAULT 0,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_bank_accounts_user_id (user_id),
    CONSTRAINT fk_bank_accounts_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------

CREATE TABLE payments (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    booking_id      BIGINT        NOT NULL,
    bank_account_id BIGINT        NULL,
    amount          DECIMAL(12,2) NOT NULL,
    transaction_code VARCHAR(100) NULL,
    status          ENUM('PENDING','CONFIRMED','FAILED') NOT NULL DEFAULT 'PENDING',
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_booking_id (booking_id),
    INDEX idx_payments_status (status),
    INDEX idx_payments_bank_account_id (bank_account_id),
    CONSTRAINT fk_payments_booking      FOREIGN KEY (booking_id)      REFERENCES bookings (id),
    CONSTRAINT fk_payments_bank_account FOREIGN KEY (bank_account_id) REFERENCES user_bank_accounts (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------

CREATE TABLE reviews (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NULL,
    type        ENUM('PLACE','FOOD','NEWS') NOT NULL,
    title       VARCHAR(255) NOT NULL,
    content     MEDIUMTEXT   NOT NULL,
    status      ENUM('PUBLISHED','HIDDEN') NOT NULL DEFAULT 'PUBLISHED',
    likes_count INT          NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_reviews_user_id (user_id),
    INDEX idx_reviews_status_type (status, type),
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------

CREATE TABLE comments (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    review_id   BIGINT      NOT NULL,
    user_id     BIGINT      NULL,
    parent_id   BIGINT      NULL,
    content     TEXT        NOT NULL,
    is_deleted  TINYINT(1)  NOT NULL DEFAULT 0,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_comments_review_id (review_id),
    INDEX idx_comments_user_id (user_id),
    INDEX idx_comments_parent_id (parent_id),
    CONSTRAINT fk_comments_review FOREIGN KEY (review_id) REFERENCES reviews  (id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_user   FOREIGN KEY (user_id)   REFERENCES users    (id) ON DELETE SET NULL,
    CONSTRAINT fk_comments_parent FOREIGN KEY (parent_id) REFERENCES comments (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------

CREATE TABLE likes (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    review_id   BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_likes_review_user (review_id, user_id),
    INDEX idx_likes_user_id (user_id),
    CONSTRAINT fk_likes_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE,
    CONSTRAINT fk_likes_user   FOREIGN KEY (user_id)   REFERENCES users   (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------

CREATE TABLE ratings (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    tour_id     BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    score       TINYINT     NOT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ratings_tour_user (tour_id, user_id),
    INDEX idx_ratings_user_id (user_id),
    CONSTRAINT fk_ratings_tour FOREIGN KEY (tour_id) REFERENCES tours (id) ON DELETE CASCADE,
    CONSTRAINT fk_ratings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_ratings_score CHECK (score >= 1 AND score <= 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

### 3.2 New Tables (Advanced Features)

```sql
-- ============================================================
-- V6: Notifications (WebSocket + ActiveMQ + RabbitMQ)
-- ============================================================

CREATE TABLE notifications (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    type        ENUM(
                    'BOOKING_CONFIRMED',
                    'BOOKING_CANCELLED',
                    'PAYMENT_CONFIRMED',
                    'TOUR_PROMOTION',
                    'SYSTEM'
                ) NOT NULL DEFAULT 'SYSTEM',
    title       VARCHAR(255) NOT NULL,
    message     TEXT         NOT NULL,
    is_read     TINYINT(1)   NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_notifications_user_id (user_id),
    INDEX idx_notifications_user_read (user_id, is_read),  -- for unread badge count
    INDEX idx_notifications_created_at (created_at),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- V7: Scheduled Job Logs (@Scheduled tracking)
-- ============================================================

CREATE TABLE scheduled_job_logs (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    job_name           VARCHAR(100) NOT NULL,
    status             ENUM('SUCCESS','FAILED','SKIPPED') NOT NULL,
    records_processed  INT          NULL DEFAULT 0,
    duration_ms        BIGINT       NULL,
    error_message      TEXT         NULL,
    executed_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_job_logs_job_name (job_name),
    INDEX idx_job_logs_executed_at (executed_at),
    INDEX idx_job_logs_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- V8: Tour Import Jobs (ThreadPoolTaskExecutor + Apache POI)
-- ============================================================

CREATE TABLE tour_import_jobs (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    file_name       VARCHAR(255)  NOT NULL,
    status          ENUM('PENDING','PROCESSING','COMPLETED','FAILED') NOT NULL DEFAULT 'PENDING',
    total_rows      INT           NULL DEFAULT 0,
    success_rows    INT           NULL DEFAULT 0,
    failed_rows     INT           NULL DEFAULT 0,
    error_details   JSON          NULL,         -- array of {row, field, reason}
    created_by      BIGINT        NOT NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at    DATETIME(6)   NULL,
    PRIMARY KEY (id),
    INDEX idx_import_jobs_created_by (created_by),
    INDEX idx_import_jobs_status (status),
    INDEX idx_import_jobs_created_at (created_at),
    CONSTRAINT fk_import_jobs_user FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- V9: Currency Rates (SOAP Web Service)
-- ============================================================

CREATE TABLE currency_rates (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    from_currency ENUM('VND','USD','EUR','JPY','KRW') NOT NULL,
    to_currency   ENUM('VND','USD','EUR','JPY','KRW') NOT NULL,
    rate          DECIMAL(15,6) NOT NULL,
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_currency_pair (from_currency, to_currency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- V10: Seed currency rates (VND base)
-- ============================================================

INSERT INTO currency_rates (from_currency, to_currency, rate) VALUES
-- VND → others
('VND', 'USD', 0.000040),
('VND', 'EUR', 0.000037),
('VND', 'JPY', 0.006100),
('VND', 'KRW', 0.054000),
-- USD → others
('USD', 'VND', 25000.000000),
('USD', 'EUR', 0.920000),
('USD', 'JPY', 152.000000),
('USD', 'KRW', 1340.000000),
-- EUR → others
('EUR', 'VND', 27000.000000),
('EUR', 'USD', 1.087000),
('EUR', 'JPY', 165.000000),
('EUR', 'KRW', 1456.000000),
-- JPY → others
('JPY', 'VND', 164.000000),
('JPY', 'USD', 0.006600),
('JPY', 'EUR', 0.006100),
('JPY', 'KRW', 8.820000),
-- KRW → others
('KRW', 'VND', 18.500000),
('KRW', 'USD', 0.000746),
('KRW', 'EUR', 0.000687),
('KRW', 'JPY', 0.113000);
```

---

## 4. Entity Relationship Diagram (Text)

```
┌──────────┐       ┌───────────────┐
│  roles   │       │ oauth_accounts│
│──────────│       │───────────────│
│ PK id    │       │ PK id         │
│    name  │       │ FK user_id ───┐
└────┬─────┘       │    provider   │
     │ 1           │    prov_uid   │
     │             └───────────────┘
     N
┌────┴───────────────────────────────────────┐
│                   users                    │
│────────────────────────────────────────────│
│ PK id                                      │
│    email (UNIQUE)                          │
│    password (nullable)                     │
│    full_name, phone, avatar_url            │
│    is_active                               │
│ FK role_id                                 │
│    created_at, updated_at                  │
└───┬──────────┬────────────┬───────────────-┘
    │1         │1           │1
    │          │            │
    N          N            N
┌───┴──────┐ ┌─┴────────┐ ┌┴──────────────────┐
│bookings  │ │ reviews  │ │ user_bank_accounts │
│──────────│ │──────────│ │───────────────────│
│ PK id    │ │ PK id    │ │ PK id             │
│ FK user_id│ │ FK user_id│ │ FK user_id       │
│ FK tour_id│ │    type  │ │    bank_name      │
│    code  │ │    title │ │    account_number │
│    status│ │    status│ │    is_default     │
└──┬──┬────┘ └──┬──┬───┘ └────────┬──────────┘
   │1 │1        │1 │1             │1
   │  │         │  │              │
   N  │1        N  N              N
┌──┴─┐│    ┌───┴┐ ┌┴──────┐  ┌──┴──────┐
│pay-││    │com-│ │likes  │  │payments │
│ments│    │ments│ │──────│  │─────────│
│────││    │────│ │PK id  │  │PK id    │
│PK  ││    │PK  │ │FK rev │  │FK book  │
│FK  ││    │FK  │ │FK user│  │FK bank  │
│book│     │rev  │ └───────┘  │amount  │
│bank│     │user │            │status  │
└────┘     │par  │            └─────────┘
           └────-┘
                              ┌──────────┐
┌──────────────┐              │ ratings  │
│  categories  │              │──────────│
│──────────────│              │ PK id    │
│ PK id        │              │ FK tour  │
│    name      │              │ FK user  │
│    desc      │              │    score │
└──────┬───────┘              └──────────┘
       │1
       │ (SET NULL on delete)
       N
┌──────┴─────────────────────┐
│          tours             │
│────────────────────────────│
│ PK id                      │
│    title, description      │
│    price, duration_days    │
│    max_participants        │
│    departure_location      │
│    destination             │
│    departure_date          │
│    status (ACTIVE/INACTIVE)│
│    avg_rating              │
│ FK category_id             │
└──────┬──────────┬──────────┘
       │1         │1
       N          N
  bookings     ratings

NEW TABLES:
┌──────────────────┐  ┌────────────────────┐
│  notifications   │  │ scheduled_job_logs │
│──────────────────│  │────────────────────│
│ PK id            │  │ PK id              │
│ FK user_id       │  │    job_name        │
│    type          │  │    status          │
│    title, message│  │    records_proc    │
│    is_read       │  │    duration_ms     │
│    created_at    │  │    error_message   │
└──────────────────┘  │    executed_at     │
                      └────────────────────┘

┌──────────────────┐  ┌──────────────────┐
│ tour_import_jobs │  │  currency_rates  │
│──────────────────│  │──────────────────│
│ PK id            │  │ PK id            │
│    file_name     │  │    from_currency │
│    status        │  │    to_currency   │
│    total_rows    │  │    rate          │
│    success_rows  │  │    updated_at    │
│    failed_rows   │  │ UNIQUE(from,to)  │
│    error_details │  └──────────────────┘
│ FK created_by    │
└──────────────────┘
```

---

## 5. Index Strategy

### 5.1 Index Inventory

| Table | Index Name | Columns | Purpose |
|---|---|---|---|
| users | `uk_users_email` | `email` | Login lookup, uniqueness |
| users | `idx_users_role_id` | `role_id` | Role JOIN |
| oauth_accounts | `uk_oauth_provider_uid` | `provider, provider_user_id` | OAuth login dedup |
| oauth_accounts | `idx_oauth_user_id` | `user_id` | User profile load |
| tours | `idx_tours_status` | `status` | Public listing filter (ACTIVE only) |
| tours | `idx_tours_category_id` | `category_id` | Category filter |
| tours | `idx_tours_departure_date` | `departure_date` | Date-range queries |
| tours | `ft_tours_search` | `title, destination, departure_location` | Full-text keyword search |
| bookings | `uk_bookings_code` | `booking_code` | Code lookup |
| bookings | `idx_bookings_user_id` | `user_id` | User's booking history |
| bookings | `idx_bookings_tour_id` | `tour_id` | Tour booking list |
| bookings | `idx_bookings_status` | `status` | Status filter |
| bookings | `idx_bookings_created_at` | `created_at` | Date-range revenue queries |
| payments | `uk_payments_booking_id` | `booking_id` | 1:1 enforcement |
| payments | `idx_payments_status` | `status` | Pending payment list |
| reviews | `idx_reviews_status_type` | `status, type` | Public listing filter |
| comments | `idx_comments_review_id` | `review_id` | Fetch comments for review |
| comments | `idx_comments_parent_id` | `parent_id` | Fetch replies |
| likes | `uk_likes_review_user` | `review_id, user_id` | Uniqueness + lookup |
| ratings | `uk_ratings_tour_user` | `tour_id, user_id` | Uniqueness + upsert |
| notifications | `idx_notifications_user_read` | `user_id, is_read` | Unread badge count query |
| notifications | `idx_notifications_created_at` | `created_at` | Recent notifications |
| scheduled_job_logs | `idx_job_logs_job_name` | `job_name` | Per-job history |
| scheduled_job_logs | `idx_job_logs_executed_at` | `executed_at` | Recent runs |
| tour_import_jobs | `idx_import_jobs_status` | `status` | Processing queue check |
| currency_rates | `uk_currency_pair` | `from_currency, to_currency` | Rate lookup |

### 5.2 Composite Index Rationale

```sql
-- Notifications: most common query is "unread count for user"
-- SELECT COUNT(*) WHERE user_id = ? AND is_read = 0
INDEX idx_notifications_user_read (user_id, is_read)

-- Reviews: most common filter is status + type together
-- SELECT * WHERE status = 'PUBLISHED' AND type = 'PLACE'
INDEX idx_reviews_status_type (status, type)

-- Bookings: admin dashboard date-range revenue query
-- SUM(total_price) WHERE status = 'CONFIRMED' AND created_at BETWEEN ? AND ?
-- Consider: INDEX idx_bookings_status_created (status, created_at)
```

---

## 6. Flyway Migration Strategy

### Directory Structure

```
src/main/resources/
├── db/
│   └── migration/
│       └── mysql/                        ← new MySQL-only directory
│           ├── V1__init_schema.sql       ← rewritten for MySQL
│           ├── V2__seed_data.sql         ← roles + admin user (same logic)
│           ├── V3__seed_tours.sql        ← same, with MySQL date format
│           ├── V4__seed_reviews.sql      ← same
│           ├── V5__unique_payment_per_booking.sql  ← already in schema V1 for MySQL
│           ├── V6__create_notifications_table.sql
│           ├── V7__create_scheduled_job_logs_table.sql
│           ├── V8__create_tour_import_jobs_table.sql
│           ├── V9__create_currency_rates_table.sql
│           └── V10__seed_currency_rates.sql
```

### application.properties — Flyway config

```properties
# Point Flyway to MySQL scripts
spring.flyway.locations=classpath:db/migration/mysql
spring.flyway.baseline-on-migrate=true
spring.flyway.validate-on-migrate=true
```

### V6 — Notifications

```sql
-- V6__create_notifications_table.sql
CREATE TABLE notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    type       ENUM('BOOKING_CONFIRMED','BOOKING_CANCELLED','PAYMENT_CONFIRMED','TOUR_PROMOTION','SYSTEM')
               NOT NULL DEFAULT 'SYSTEM',
    title      VARCHAR(255) NOT NULL,
    message    TEXT         NOT NULL,
    is_read    TINYINT(1)   NOT NULL DEFAULT 0,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_notifications_user_id   (user_id),
    INDEX idx_notifications_user_read (user_id, is_read),
    INDEX idx_notifications_created_at (created_at),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### V7 — Scheduled Job Logs

```sql
-- V7__create_scheduled_job_logs_table.sql
CREATE TABLE scheduled_job_logs (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    job_name          VARCHAR(100) NOT NULL,
    status            ENUM('SUCCESS','FAILED','SKIPPED') NOT NULL,
    records_processed INT          NULL DEFAULT 0,
    duration_ms       BIGINT       NULL,
    error_message     TEXT         NULL,
    executed_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_job_logs_job_name   (job_name),
    INDEX idx_job_logs_executed_at (executed_at),
    INDEX idx_job_logs_status      (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### V8 — Tour Import Jobs

```sql
-- V8__create_tour_import_jobs_table.sql
CREATE TABLE tour_import_jobs (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    file_name     VARCHAR(255) NOT NULL,
    status        ENUM('PENDING','PROCESSING','COMPLETED','FAILED') NOT NULL DEFAULT 'PENDING',
    total_rows    INT          NULL DEFAULT 0,
    success_rows  INT          NULL DEFAULT 0,
    failed_rows   INT          NULL DEFAULT 0,
    error_details JSON         NULL,
    created_by    BIGINT       NOT NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    INDEX idx_import_jobs_created_by (created_by),
    INDEX idx_import_jobs_status     (status),
    INDEX idx_import_jobs_created_at (created_at),
    CONSTRAINT fk_import_jobs_user FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### V9 — Currency Rates

```sql
-- V9__create_currency_rates_table.sql
CREATE TABLE currency_rates (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    from_currency ENUM('VND','USD','EUR','JPY','KRW') NOT NULL,
    to_currency   ENUM('VND','USD','EUR','JPY','KRW') NOT NULL,
    rate          DECIMAL(15,6) NOT NULL,
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_currency_pair (from_currency, to_currency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 7. application.properties — MySQL Config

### application-dev.properties

```properties
# ── DataSource ──────────────────────────────────────────
spring.datasource.url=jdbc:mysql://localhost:3306/booking_tours?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&characterEncoding=UTF-8
spring.datasource.username=root
spring.datasource.password=root
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# ── Connection Pool (HikariCP) ───────────────────────────
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
spring.datasource.hikari.connection-test-query=SELECT 1

# ── JPA / Hibernate ──────────────────────────────────────
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.open-in-view=false

# ── Flyway ───────────────────────────────────────────────
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration/mysql
spring.flyway.baseline-on-migrate=true
```

### application-prod.properties

```properties
# ── DataSource ──────────────────────────────────────────
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# ── Connection Pool (HikariCP) ───────────────────────────
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
spring.datasource.hikari.connection-test-query=SELECT 1

# ── JPA / Hibernate ──────────────────────────────────────
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=false
spring.jpa.open-in-view=false

# ── Flyway ───────────────────────────────────────────────
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration/mysql
spring.flyway.validate-on-migrate=true
```

### pom.xml dependency (replace PostgreSQL driver)

```xml
<!-- Remove PostgreSQL -->
<!-- <dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency> -->

<!-- Add MySQL -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

---

## 8. Data Volume Estimates (1-week dev scope)

| Table | Seed Rows | Expected Growth |
|---|---|---|
| roles | 2 | Static |
| users | 10–20 | Low |
| categories | 5–10 | Low |
| tours | 20–30 | Medium |
| bookings | 30–50 | High |
| payments | 20–40 | High |
| reviews | 10–20 | Medium |
| comments | 20–40 | Medium |
| notifications | 50–200 | High (auto-generated) |
| scheduled_job_logs | 0 | Auto (daily jobs) |
| currency_rates | 20 | Static (seeded) |
