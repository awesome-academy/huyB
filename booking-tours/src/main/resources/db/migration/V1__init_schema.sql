-- ================================================================
-- SUN Booking Tours — V1: Initial Schema
-- PostgreSQL · 12 tables
-- ================================================================

-- ----------------------------------------------------------------
-- 1. roles
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS roles (
    id   BIGSERIAL    PRIMARY KEY,
    name VARCHAR(20)  UNIQUE NOT NULL   -- ADMIN, USER
);

-- ----------------------------------------------------------------
-- 2. users
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id         BIGSERIAL    PRIMARY KEY,
    email      VARCHAR(255) UNIQUE NOT NULL,
    password   VARCHAR(255),                        -- NULL khi login OAuth2
    full_name  VARCHAR(255) NOT NULL,
    phone      VARCHAR(20),
    avatar_url TEXT,
    role_id    BIGINT       REFERENCES roles (id),
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ----------------------------------------------------------------
-- 3. oauth_accounts
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oauth_accounts (
    id               BIGSERIAL    PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider         VARCHAR(20)  NOT NULL,          -- GOOGLE, FACEBOOK, TWITTER
    provider_user_id VARCHAR(255) NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (provider, provider_user_id)
);

-- ----------------------------------------------------------------
-- 4. user_bank_accounts
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_bank_accounts (
    id             BIGSERIAL    PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    bank_name      VARCHAR(100) NOT NULL,
    account_number VARCHAR(50)  NOT NULL,
    account_holder VARCHAR(255) NOT NULL,
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ----------------------------------------------------------------
-- 5. categories
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categories (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) UNIQUE NOT NULL,
    description TEXT
);

-- ----------------------------------------------------------------
-- 6. tours
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tours (
    id                 BIGSERIAL      PRIMARY KEY,
    title              VARCHAR(255)   NOT NULL,
    description        TEXT           NOT NULL,
    price              NUMERIC(12, 2) NOT NULL,
    duration_days      INT            NOT NULL,
    max_participants   INT            NOT NULL,
    departure_location VARCHAR(255)   NOT NULL,
    destination        VARCHAR(255)   NOT NULL,
    departure_date     DATE,
    thumbnail_url      TEXT,
    category_id        BIGINT         REFERENCES categories (id) ON DELETE SET NULL,
    status             VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, INACTIVE
    avg_rating         NUMERIC(2, 1)  NOT NULL DEFAULT 0.0,
    created_at         TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- ----------------------------------------------------------------
-- 7. bookings
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bookings (
    id           BIGSERIAL      PRIMARY KEY,
    booking_code VARCHAR(20)    UNIQUE NOT NULL,   -- BK-YYYYMMDD-XXXX
    user_id      BIGINT         NOT NULL REFERENCES users (id),
    tour_id      BIGINT         NOT NULL REFERENCES tours (id),
    participants INT            NOT NULL,
    total_price  NUMERIC(12, 2) NOT NULL,
    status       VARCHAR(20)    NOT NULL DEFAULT 'PENDING',  -- PENDING, CONFIRMED, CANCELLED, COMPLETED
    note         TEXT,
    created_at   TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- ----------------------------------------------------------------
-- 8. payments
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments (
    id               BIGSERIAL      PRIMARY KEY,
    booking_id       BIGINT         NOT NULL REFERENCES bookings (id),
    amount           NUMERIC(12, 2) NOT NULL,
    bank_account_id  BIGINT         REFERENCES user_bank_accounts (id) ON DELETE SET NULL,
    transaction_code VARCHAR(255),
    status           VARCHAR(20)    NOT NULL DEFAULT 'PENDING',  -- PENDING, CONFIRMED, FAILED
    created_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- ----------------------------------------------------------------
-- 9. reviews
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reviews (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    type        VARCHAR(20)  NOT NULL,               -- PLACE, FOOD, NEWS
    title       VARCHAR(255) NOT NULL,
    content     TEXT         NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED',  -- PUBLISHED, HIDDEN
    likes_count INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ----------------------------------------------------------------
-- 10. comments
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS comments (
    id         BIGSERIAL PRIMARY KEY,
    review_id  BIGINT    NOT NULL REFERENCES reviews (id) ON DELETE CASCADE,
    user_id    BIGINT    NOT NULL REFERENCES users (id),
    parent_id  BIGINT    REFERENCES comments (id) ON DELETE CASCADE,  -- NULL = gốc; NOT NULL = reply (1 cấp)
    content    TEXT      NOT NULL,
    is_deleted BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ----------------------------------------------------------------
-- 11. likes
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS likes (
    id         BIGSERIAL PRIMARY KEY,
    review_id  BIGINT    NOT NULL REFERENCES reviews (id) ON DELETE CASCADE,
    user_id    BIGINT    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (review_id, user_id)
);

-- ----------------------------------------------------------------
-- 12. ratings
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ratings (
    id         BIGSERIAL PRIMARY KEY,
    tour_id    BIGINT    NOT NULL REFERENCES tours (id) ON DELETE CASCADE,
    user_id    BIGINT    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    score      SMALLINT  NOT NULL CHECK (score BETWEEN 1 AND 5),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (tour_id, user_id)   -- mỗi user chỉ rating 1 lần / tour
);

