package com.sunasterisk.bookingtours.service;

import com.sunasterisk.bookingtours.dto.PaymentRequest;
import com.sunasterisk.bookingtours.entity.Payment;

import java.util.Optional;

/**
 * Service quản lý nghiệp vụ thanh toán (Payment).
 */
public interface PaymentService {

    /**
     * Tạo mới Payment với trạng thái PENDING.
     *
     * <p>Luồng:
     * <ol>
     *   <li>Xác thực user sở hữu booking.</li>
     *   <li>Booking phải ở trạng thái PENDING.</li>
     *   <li>Kiểm tra booking chưa có payment trước đó.</li>
     *   <li>Xác thực tài khoản ngân hàng thuộc về user.</li>
     *   <li>Tạo Payment(status=PENDING) và lưu DB.</li>
     * </ol>
     *
     * @param email     email của user đang đăng nhập
     * @param bookingId id của booking cần thanh toán
     * @param request   DTO chứa bankAccountId và transactionCode
     * @return Payment vừa tạo
     */
    Payment createPayment(String email, Long bookingId, PaymentRequest request);

    /**
     * Lấy payment theo bookingId.
     *
     * @param bookingId id của booking
     * @return Optional Payment
     */
    Optional<Payment> findByBookingId(Long bookingId);
}
