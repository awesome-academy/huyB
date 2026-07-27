package com.sunasterisk.bookingtours.repository;

import com.sunasterisk.bookingtours.entity.Payment;
import com.sunasterisk.bookingtours.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho {@link Payment}.
 * Cung cấp các truy vấn cơ bản và các finder theo booking / status.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * Tìm payment theo booking id.
     */
    Optional<Payment> findByBookingId(Long bookingId);

    /**
     * Lấy danh sách payment theo status (dùng cho Admin).
     */
    List<Payment> findByStatus(PaymentStatus status);

    /**
     * Kiểm tra booking đã có payment chưa.
     */
    boolean existsByBookingId(Long bookingId);
}
