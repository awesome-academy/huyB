package com.sunasterisk.bookingtours.entity;

/**
 * Trạng thái vòng đời của một booking (đặt tour).
 *
 * <p>Luồng chuyển trạng thái:
 * <pre>
 *   PENDING ──► CONFIRMED  (Admin xác nhận thanh toán thành công)
 *           ──► CANCELLED  (User tự hủy hoặc Admin từ chối)
 *   CONFIRMED ──► COMPLETED (Tour đã diễn ra xong)
 * </pre>
 */
public enum BookingStatus {

    /**
     * Booking vừa được tạo, chờ user thanh toán và admin xác nhận.
     * Đây là trạng thái mặc định khi tạo booking.
     */
    PENDING,

    /**
     * Admin đã xác nhận thanh toán thành công.
     * Booking được giữ chỗ chính thức cho user.
     */
    CONFIRMED,

    /**
     * Booking bị hủy — do user tự hủy (khi còn PENDING)
     * hoặc do Admin từ chối / hủy bỏ.
     */
    CANCELLED,

    /**
     * Tour đã diễn ra và kết thúc.
     * User có thể viết review và rating sau khi ở trạng thái này.
     */
    COMPLETED
}
