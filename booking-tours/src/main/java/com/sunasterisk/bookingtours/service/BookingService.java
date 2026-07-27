package com.sunasterisk.bookingtours.service;

import com.sunasterisk.bookingtours.dto.BookingRequest;
import com.sunasterisk.bookingtours.entity.Booking;
import com.sunasterisk.bookingtours.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    /**
     * Lấy danh sách booking của user (lịch sử booking), có lọc theo status.
     *
     * @param email    email của user đang đăng nhập
     * @param status   trạng thái cần lọc, {@code null} = tất cả
     * @param pageable thông tin phân trang
     * @return trang kết quả booking
     */
    Page<Booking> getBookingHistory(String email, BookingStatus status, Pageable pageable);

    /**
     * Lấy chi tiết booking theo id; chỉ user sở hữu mới được xem.
     *
     * @param email     email của user đang đăng nhập
     * @param bookingId id của booking
     * @return Booking (đã fetch tour & user)
     * @throws com.sunasterisk.bookingtours.exception.ResourceNotFoundException nếu không tìm thấy
     * @throws org.springframework.security.access.AccessDeniedException nếu booking không thuộc user
     */
    Booking getBookingDetail(String email, Long bookingId);

    /**
     * Hủy booking ở trạng thái PENDING.
     * Chỉ user sở hữu booking mới được phép hủy.
     *
     * @param email     email của user đang đăng nhập
     * @param bookingId id của booking cần hủy
     * @throws com.sunasterisk.bookingtours.exception.ResourceNotFoundException nếu không tìm thấy
     * @throws IllegalStateException nếu booking không ở trạng thái PENDING
     * @throws org.springframework.security.access.AccessDeniedException nếu không phải chủ sở hữu
     */
    void cancelBooking(String email, Long bookingId);
}
