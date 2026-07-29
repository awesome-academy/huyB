---
phase: 04
title: V2–V5 Seed files — Fix MySQL syntax
status: pending
---

## V2__seed_data.sql

**Vấn đề:** `ON CONFLICT (name) DO NOTHING` — không hỗ trợ trong MySQL

**Fix:** Đổi `INSERT INTO` → `INSERT IGNORE INTO`, xóa `ON CONFLICT` clause:
```sql
-- Before (PostgreSQL)
INSERT INTO roles (name) VALUES ('ADMIN'), ('USER') ON CONFLICT (name) DO NOTHING;

-- After (MySQL)
INSERT IGNORE INTO roles (name) VALUES ('ADMIN'), ('USER');
```

## V3__seed_tours.sql

**Vấn đề:** `ON CONFLICT DO NOTHING` ở cuối INSERT block

**Fix:** Đổi `INSERT INTO tours` → `INSERT IGNORE INTO tours`, xóa `ON CONFLICT DO NOTHING`

## V4__seed_reviews.sql

**Vấn đề:** `ON CONFLICT DO NOTHING` ở cuối INSERT block

**Fix:** Đổi `INSERT INTO reviews` → `INSERT IGNORE INTO reviews`, xóa `ON CONFLICT DO NOTHING`

## V5__unique_payment_per_booking.sql

**Vấn đề:** `DELETE FROM payments p USING payments p2 WHERE ...` — không hỗ trợ MySQL syntax

**Fix (MySQL DELETE JOIN):**
```sql
-- Before (PostgreSQL)
DELETE FROM payments p
USING payments p2
WHERE p.booking_id = p2.booking_id
  AND p.id > p2.id;

-- After (MySQL)
DELETE p FROM payments p
INNER JOIN payments p2
    ON p.booking_id = p2.booking_id
    AND p.id > p2.id;
```

`ALTER TABLE payments ADD CONSTRAINT uq_payments_booking_id UNIQUE (booking_id)` — tương thích MySQL ✓

## Todo
- [ ] Fix V2: INSERT IGNORE + xóa ON CONFLICT
- [ ] Fix V3: INSERT IGNORE + xóa ON CONFLICT DO NOTHING
- [ ] Fix V4: INSERT IGNORE + xóa ON CONFLICT DO NOTHING
- [ ] Fix V5: DELETE JOIN syntax
