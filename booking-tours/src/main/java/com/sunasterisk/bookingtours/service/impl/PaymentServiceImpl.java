package com.sunasterisk.bookingtours.service.impl;

import com.sunasterisk.bookingtours.dto.PaymentRequest;
import com.sunasterisk.bookingtours.entity.*;
import com.sunasterisk.bookingtours.exception.ResourceNotFoundException;
import com.sunasterisk.bookingtours.repository.BookingRepository;
import com.sunasterisk.bookingtours.repository.PaymentRepository;
import com.sunasterisk.bookingtours.repository.UserBankAccountRepository;
import com.sunasterisk.bookingtours.repository.UserRepository;
import com.sunasterisk.bookingtours.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Triển khai {@link PaymentService}.
 */
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final UserBankAccountRepository bankAccountRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Payment createPayment(String email, Long bookingId, PaymentRequest request) {
        // 1. Lấy user
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        // 2. Lấy booking (kèm tour & user để truy cập total_price)
        Booking booking = bookingRepository.findByIdWithTourAndUser(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        // 3. Kiểm tra quyền sở hữu
        if (!booking.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have permission to pay for this booking.");
        }

        // 4. Booking phải ở trạng thái PENDING
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException(
                    "Payment is only allowed for PENDING bookings. Current status: " + booking.getStatus());
        }

        // 5. Kiểm tra booking chưa có payment
        if (paymentRepository.existsByBookingId(bookingId)) {
            throw new IllegalStateException(
                    "This booking already has a payment record. Please wait for admin confirmation.");
        }

        // 6. Xác thực tài khoản ngân hàng thuộc về user
        UserBankAccount bankAccount = bankAccountRepository.findById(request.getBankAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Bank account", request.getBankAccountId()));

        if (!bankAccount.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have permission to use this bank account.");
        }

        // 7. Tạo Payment PENDING
        Payment payment = Payment.builder()
                .booking(booking)
                .amount(booking.getTotalPrice())
                .bankAccount(bankAccount)
                .transactionCode(request.getTransactionCode().trim())
                .status(PaymentStatus.PENDING)
                .build();

        try {
            // saveAndFlush để unique constraint (uq_payments_booking_id) được
            // kiểm tra ngay tại đây — bắt được race mà check ở bước 5 bỏ lọt
            // khi hai request cùng submit payment cho một booking.
            return paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(
                    "This booking already has a payment record. Please wait for admin confirmation.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findByBookingId(Long bookingId) {
        Optional<Payment> payment = paymentRepository.findByBookingId(bookingId);
        // Khởi tạo lazy proxy BankAccount trong khi session còn mở
        // để Thymeleaf có thể truy cập payment.bankAccount sau khi transaction đóng
        payment.ifPresent(value -> Hibernate.initialize(value.getBankAccount()));
        return payment;
    }
}
