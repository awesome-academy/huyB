-- ================================================================
-- SUN Booking Tours — V6: Seed Admin User
-- INSERT IGNORE → idempotent (chạy lại không bị duplicate)
-- Password: Admin@123  (BCrypt $2a$10)
-- ================================================================

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
