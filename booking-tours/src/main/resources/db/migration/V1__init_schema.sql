-- ================================================================
-- SUN Booking Tours — V1: Initial Schema
-- MySQL 8.x · InnoDB · utf8mb4 · 12 tables
-- ================================================================

-- ----------------------------------------------------------------
-- 1. roles
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS roles (
    id   BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(20) UNIQUE NOT NULL   -- ADMIN, USER
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------
-- 2. users
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email      VARCHAR(255) UNIQUE NOT NULL,
    password   VARCHAR(255),                        -- NULL khi login OAuth2
    full_name  VARCHAR(255) NOT NULL,
    phone      VARCHAR(20),
    avatar_url TEXT,
    role_id    BIGINT,
    is_active  TINYINT(1)  NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------
-- 3. oauth_accounts
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oauth_accounts (
    id               BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT      NOT NULL,
    provider         VARCHAR(20) NOT NULL,          -- GOOGLE, FACEBOOK, TWITTER
    provider_user_id VARCHAR(255) NOT NULL,
    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_oauth_provider_user (provider, provider_user_id),
    CONSTRAINT fk_oauth_accounts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------
-- 4. user_bank_accounts
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_bank_accounts (
    id             BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT      NOT NULL,
    bank_name      VARCHAR(100) NOT NULL,
    account_number VARCHAR(50)  NOT NULL,
    account_holder VARCHAR(255) NOT NULL,
    is_default     TINYINT(1)  NOT NULL DEFAULT 0,
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_bank_accounts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------
-- 5. categories
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categories (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) UNIQUE NOT NULL,
    description TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------
-- 6. tours
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tours (
    id                 BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title              VARCHAR(255) NOT NULL,
    description        TEXT         NOT NULL,
    price              DECIMAL(12, 2) NOT NULL,
    duration_days      INT          NOT NULL,
    max_participants   INT          NOT NULL,
    departure_location VARCHAR(255) NOT NULL,
    destination        VARCHAR(255) NOT NULL,
    departure_date     DATE,
    thumbnail_url      TEXT,
    category_id        BIGINT,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, INACTIVE
    avg_rating         DECIMAL(2, 1) NOT NULL DEFAULT 0.0,
    created_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_tours_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------
-- 7. bookings
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bookings (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_code VARCHAR(20)  UNIQUE NOT NULL,   -- BK-YYYYMMDD-XXXX
    user_id      BIGINT       NOT NULL,
    tour_id      BIGINT       NOT NULL,
    participants INT          NOT NULL,
    total_price  DECIMAL(12, 2) NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING, CONFIRMED, CANCELLED, COMPLETED
    note         TEXT,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_bookings_tour FOREIGN KEY (tour_id) REFERENCES tours (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------
-- 8. payments
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_id       BIGINT       NOT NULL,
    amount           DECIMAL(12, 2) NOT NULL,
    bank_account_id  BIGINT,
    transaction_code VARCHAR(255),
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING, CONFIRMED, FAILED
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_payments_booking      FOREIGN KEY (booking_id)      REFERENCES bookings (id),
    CONSTRAINT fk_payments_bank_account FOREIGN KEY (bank_account_id) REFERENCES user_bank_accounts (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------
-- 9. reviews
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reviews (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    type        VARCHAR(20) NOT NULL,               -- PLACE, FOOD, NEWS
    title       VARCHAR(255) NOT NULL,
    content     TEXT        NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',  -- PUBLISHED, HIDDEN
    likes_count INT         NOT NULL DEFAULT 0,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------
-- 10. comments
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS comments (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    review_id  BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    parent_id  BIGINT,                              -- NULL = gốc; NOT NULL = reply (1 cấp)
    content    TEXT        NOT NULL,
    is_deleted TINYINT(1)  NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_comments_review  FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_user    FOREIGN KEY (user_id)   REFERENCES users (id),
    CONSTRAINT fk_comments_parent  FOREIGN KEY (parent_id) REFERENCES comments (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------
-- 11. likes
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS likes (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    review_id  BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_likes_review_user (review_id, user_id),
    CONSTRAINT fk_likes_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE,
    CONSTRAINT fk_likes_user   FOREIGN KEY (user_id)   REFERENCES users (id)   ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------
-- 12. ratings
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ratings (
    id         BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tour_id    BIGINT    NOT NULL,
    user_id    BIGINT    NOT NULL,
    score      SMALLINT  NOT NULL CHECK (score BETWEEN 1 AND 5),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_ratings_tour_user (tour_id, user_id),   -- mỗi user chỉ rating 1 lần / tour
    CONSTRAINT fk_ratings_tour FOREIGN KEY (tour_id) REFERENCES tours (id) ON DELETE CASCADE,
    CONSTRAINT fk_ratings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
