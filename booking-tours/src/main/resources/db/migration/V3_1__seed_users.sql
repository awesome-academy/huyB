-- ================================================================
-- SUN Booking Tours — V3.1: Seed Users
-- Must run BEFORE V4 (seed_reviews) — reviews FK references user IDs 2, 3, 4.
-- INSERT IGNORE → idempotent (chạy lại không bị duplicate)
-- Passwords (BCrypt $2b$10): Admin@123 (admin), User@123 (regular users)
-- ================================================================

-- Admin user first → AUTO_INCREMENT id = 1
INSERT IGNORE INTO users (email, password, full_name, phone, role_id, is_active)
SELECT 'admin@bookingtours.com',
       '$2a$10$ktIAV.XSdQ6uLeqhK86FH.xTvQApEklRTnT75AY0SFXkEhfXu/2yW',
       'Administrator',
       NULL,
       r.id,
       1
FROM roles r
WHERE r.name = 'ADMIN'
LIMIT 1;

-- Regular users → AUTO_INCREMENT ids = 2, 3, 4
INSERT IGNORE INTO users (email, password, full_name, phone, role_id, is_active)
SELECT 'nguyen.thi.lan@example.com',
       '$2b$10$GaEJ3eTtjlKjBaN.V/daxeBaSxhAgDC1FCD3CIWVx0WXQnRwEVvlS',
       'Nguyễn Thị Lan',
       '0901234567',
       r.id,
       1
FROM roles r WHERE r.name = 'USER' LIMIT 1;

INSERT IGNORE INTO users (email, password, full_name, phone, role_id, is_active)
SELECT 'tran.van.minh@example.com',
       '$2b$10$mqsxks7iiBjwFPiN7fzVEulTQRo8XS0KzKDJAewCt.UJNxAgJjmUu',
       'Trần Văn Minh',
       '0912345678',
       r.id,
       1
FROM roles r WHERE r.name = 'USER' LIMIT 1;

INSERT IGNORE INTO users (email, password, full_name, phone, role_id, is_active)
SELECT 'le.thi.hoa@example.com',
       '$2b$10$.Im0ZL72qVohRHbgeoKqLea19Rs4u7sx/Na8DdatybQ.R9nsEEbBu',
       'Lê Thị Hoa',
       '0923456789',
       r.id,
       1
FROM roles r WHERE r.name = 'USER' LIMIT 1;
