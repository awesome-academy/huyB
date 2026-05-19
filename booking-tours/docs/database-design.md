# Database Design — SUN Booking Tours

> Mock project · 12 bảng · PostgreSQL + Spring Data JPA

---

## 1. Sơ đồ quan hệ

```
roles ──── users ──── oauth_accounts
              |
              |──── user_bank_accounts
              |
              |──── bookings ──── payments
              |         └──── tours ──── categories
              |
              |──── reviews ──── comments (parent_id self-ref)
              |          └────── likes
              └──── ratings
```

---

## 2. Chi tiết bảng

### 2.1 `roles`

| Column | Type | Ghi chú |
|--------|------|---------|
| id | BIGSERIAL PK | |
| name | VARCHAR(20) UNIQUE NOT NULL | ADMIN, USER |

**Seed:** `ADMIN`, `USER`

---

### 2.2 `users`

| Column | Type | Ghi chú |
|--------|------|---------|
| id | BIGSERIAL PK | |
| email | VARCHAR(255) UNIQUE NOT NULL | |
| password | VARCHAR(255) | NULL nếu đăng nhập OAuth2 |
| full_name | VARCHAR(255) NOT NULL | |
| phone | VARCHAR(20) | |
| avatar_url | TEXT | |
| role_id | BIGINT FK → roles.id | |
| is_active | BOOLEAN DEFAULT TRUE | |
| created_at | TIMESTAMP DEFAULT NOW() | |
| updated_at | TIMESTAMP DEFAULT NOW() | |

---

### 2.3 `oauth_accounts`

| Column | Type | Ghi chú |
|--------|------|---------|
| id | BIGSERIAL PK | |
| user_id | BIGINT FK → users.id | |
| provider | VARCHAR(20) NOT NULL | GOOGLE, FACEBOOK, TWITTER |
| provider_user_id | VARCHAR(255) NOT NULL | |
| created_at | TIMESTAMP DEFAULT NOW() | |

**Unique:** `(provider, provider_user_id)`

---

### 2.4 `user_bank_accounts`

| Column | Type | Ghi chú |
|--------|------|---------|
| id | BIGSERIAL PK | |
| user_id | BIGINT FK → users.id | |
| bank_name | VARCHAR(100) NOT NULL | |
| account_number | VARCHAR(50) NOT NULL | |
| account_holder | VARCHAR(255) NOT NULL | |
| is_default | BOOLEAN DEFAULT FALSE | |
| created_at | TIMESTAMP DEFAULT NOW() | |

---

### 2.5 `categories`

| Column | Type | Ghi chú |
|--------|------|---------|
| id | BIGSERIAL PK | |
| name | VARCHAR(100) UNIQUE NOT NULL | |
| description | TEXT | |

---

### 2.6 `tours`

| Column | Type | Ghi chú |
|--------|------|---------|
| id | BIGSERIAL PK | |
| title | VARCHAR(255) NOT NULL | |
| description | TEXT NOT NULL | |
| price | NUMERIC(12,2) NOT NULL | Giá / người |
| duration_days | INT NOT NULL | |
| max_participants | INT NOT NULL | |
| departure_location | VARCHAR(255) NOT NULL | |
| destination | VARCHAR(255) NOT NULL | |
| departure_date | DATE | |
| thumbnail_url | TEXT | |
| category_id | BIGINT FK → categories.id | |
| status | VARCHAR(20) DEFAULT 'ACTIVE' | ACTIVE, INACTIVE |
| avg_rating | NUMERIC(2,1) DEFAULT 0 | Cached, cập nhật khi có rating mới |
| created_at | TIMESTAMP DEFAULT NOW() | |
| updated_at | TIMESTAMP DEFAULT NOW() | |

---

### 2.7 `bookings`

| Column | Type | Ghi chú |
|--------|------|---------|
| id | BIGSERIAL PK | |
| booking_code | VARCHAR(20) UNIQUE NOT NULL | BK-YYYYMMDD-XXXX |
| user_id | BIGINT FK → users.id | |
| tour_id | BIGINT FK → tours.id | |
| participants | INT NOT NULL | |
| total_price | NUMERIC(12,2) NOT NULL | price × participants |
| status | VARCHAR(20) DEFAULT 'PENDING' | PENDING → CONFIRMED / CANCELLED / COMPLETED |
| note | TEXT | |
| created_at | TIMESTAMP DEFAULT NOW() | |
| updated_at | TIMESTAMP DEFAULT NOW() | |

---

### 2.8 `payments`

| Column | Type | Ghi chú |
|--------|------|---------|
| id | BIGSERIAL PK | |
| booking_id | BIGINT FK → bookings.id | |
| amount | NUMERIC(12,2) NOT NULL | |
| bank_account_id | BIGINT FK → user_bank_accounts.id | Tài khoản dùng để thanh toán |
| transaction_code | VARCHAR(255) | Mã GD do user nhập |
| status | VARCHAR(20) DEFAULT 'PENDING' | PENDING, CONFIRMED, FAILED |
| created_at | TIMESTAMP DEFAULT NOW() | |
| updated_at | TIMESTAMP DEFAULT NOW() | |

---

### 2.9 `reviews`

| Column | Type | Ghi chú |
|--------|------|---------|
| id | BIGSERIAL PK | |
| user_id | BIGINT FK → users.id | |
| type | VARCHAR(20) NOT NULL | PLACE, FOOD, NEWS |
| title | VARCHAR(255) NOT NULL | |
| content | TEXT NOT NULL | |
| status | VARCHAR(20) DEFAULT 'PUBLISHED' | PUBLISHED, HIDDEN |
| likes_count | INT DEFAULT 0 | Cached counter |
| created_at | TIMESTAMP DEFAULT NOW() | |
| updated_at | TIMESTAMP DEFAULT NOW() | |

---

### 2.10 `comments`

| Column | Type | Ghi chú |
|--------|------|---------|
| id | BIGSERIAL PK | |
| review_id | BIGINT FK → reviews.id | |
| user_id | BIGINT FK → users.id | |
| parent_id | BIGINT FK → comments.id | NULL = comment gốc; NOT NULL = reply |
| content | TEXT NOT NULL | |
| is_deleted | BOOLEAN DEFAULT FALSE | Soft delete |
| created_at | TIMESTAMP DEFAULT NOW() | |

> Chỉ cho phép reply 1 cấp: comment → reply (reply không được reply tiếp)

---

### 2.11 `likes`

| Column | Type | Ghi chú |
|--------|------|---------|
| id | BIGSERIAL PK | |
| review_id | BIGINT FK → reviews.id | |
| user_id | BIGINT FK → users.id | |
| created_at | TIMESTAMP DEFAULT NOW() | |

**Unique:** `(review_id, user_id)`

---

### 2.12 `ratings`

| Column | Type | Ghi chú |
|--------|------|---------|
| id | BIGSERIAL PK | |
| tour_id | BIGINT FK → tours.id | |
| user_id | BIGINT FK → users.id | |
| score | SMALLINT NOT NULL | CHECK (score BETWEEN 1 AND 5) |
| created_at | TIMESTAMP DEFAULT NOW() | |

**Unique:** `(tour_id, user_id)` — mỗi user chỉ rating 1 lần / tour

---

## 3. Enums

| Enum | Giá trị |
|------|---------|
| Role | ADMIN, USER |
| OAuth Provider | GOOGLE, FACEBOOK, TWITTER |
| Tour Status | ACTIVE, INACTIVE |
| Booking Status | PENDING, CONFIRMED, CANCELLED, COMPLETED |
| Payment Status | PENDING, CONFIRMED, FAILED |
| Review Type | PLACE, FOOD, NEWS |
| Review Status | PUBLISHED, HIDDEN |

---

## 4. ERD (Text)

```
roles (1) ──── (N) users
users (1) ──── (N) oauth_accounts
users (1) ──── (N) user_bank_accounts
users (1) ──── (N) bookings ──── (1) payments
users (1) ──── (N) reviews  ──── (N) comments ──── (N) comments [parent]
users (1) ──── (N) likes
users (1) ──── (N) ratings
tours (N) ──── (1) categories
tours (1) ──── (N) bookings
tours (1) ──── (N) ratings
reviews (1) ── (N) likes
```

