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
import org.springframework.security.access.AccessDeniedException;
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

        return paymentRepository.save(payment);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findByBookingId(Long bookingId) {
        return paymentRepository.findByBookingId(bookingId);
    }
}
