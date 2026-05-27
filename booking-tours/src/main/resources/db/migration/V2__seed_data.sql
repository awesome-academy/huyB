-- ================================================================
-- SUN Booking Tours — V2: Seed Data
-- ON CONFLICT DO NOTHING → idempotent (chạy lại không bị duplicate)
-- ================================================================

-- ----------------------------------------------------------------
-- Roles  (bắt buộc — dùng trong Security)
-- ----------------------------------------------------------------
INSERT INTO roles (name)
VALUES ('ADMIN'),
       ('USER')
ON CONFLICT (name) DO NOTHING;

-- ----------------------------------------------------------------
-- Categories mẫu
-- ----------------------------------------------------------------
INSERT INTO categories (name, description)
VALUES ('Du lịch biển',       'Các tour tham quan, nghỉ dưỡng tại các bãi biển nổi tiếng'),
       ('Du lịch núi',        'Khám phá thiên nhiên và cảnh quan vùng núi hùng vĩ'),
       ('Du lịch văn hóa',    'Tìm hiểu di sản lịch sử và văn hóa địa phương'),
       ('Du lịch mạo hiểm',   'Trải nghiệm các hoạt động ngoài trời kích thích'),
       ('Du lịch nghỉ dưỡng', 'Tận hưởng kỳ nghỉ thư giãn tại resort cao cấp')
ON CONFLICT (name) DO NOTHING;

