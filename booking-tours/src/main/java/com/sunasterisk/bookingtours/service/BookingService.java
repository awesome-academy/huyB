package com.sunasterisk.bookingtours.service;

import com.sunasterisk.bookingtours.dto.BookingRequest;
import com.sunasterisk.bookingtours.entity.Booking;

/**
 * Service quản lý nghiệp vụ đặt tour (Booking).
 */
public interface BookingService {

    /**
     * Tạo mới booking với trạng thái PENDING.
     * Tự động generate {@code booking_code} theo format {@code BK-YYYYMMDD-XXXX}.
     * Tính {@code total_price = tour.price × participants}.
     *
     * @param email   email của user đang đăng nhập
     * @param request DTO chứa tourId, số người, ghi chú
     * @return Booking vừa được lưu
     * @throws com.sunasterisk.bookingtours.exception.ResourceNotFoundException nếu tour không tìm thấy
     * @throws IllegalArgumentException nếu số người vượt quá {@code tour.maxParticipants}
     */
    Booking createBooking(String email, BookingRequest request);

    /**
     * Sinh mã booking duy nhất theo format {@code BK-YYYYMMDD-XXXX}.
     * Kiểm tra trùng trong DB; retry tối đa 10 lần nếu trùng.
     *
     * @return mã booking chưa tồn tại trong DB
     */
    String generateBookingCode();
}
