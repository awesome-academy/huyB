-- ================================================================
-- SUN Booking Tours — V5: mỗi booking chỉ có tối đa một payment
--
-- PaymentServiceImpl kiểm tra existsByBookingId trước khi insert,
-- nhưng check-then-act không atomic: hai request đồng thời có thể
-- cùng vượt qua check và tạo 2 payment cho 1 booking.
-- Unique constraint ở DB là guard cuối cùng.
-- ================================================================

-- Dọn duplicate nếu đã tồn tại (giữ payment cũ nhất theo id)
DELETE FROM payments p
USING payments p2
WHERE p.booking_id = p2.booking_id
  AND p.id > p2.id;

ALTER TABLE payments
    ADD CONSTRAINT uq_payments_booking_id UNIQUE (booking_id);
