package com.sunasterisk.bookingtours.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Entity đại diện cho một giao dịch thanh toán trong hệ thống.
 * Kế thừa {@link BaseEntity} để tự động quản lý created_at / updated_at.
 *
 * <p>Quan hệ:
 * <ul>
 *   <li>N payment → 1 {@link Booking} (booking được thanh toán)</li>
 *   <li>N payment → 1 {@link UserBankAccount} (tài khoản ngân hàng dùng để thanh toán)</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"booking", "bankAccount"})
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Booking được thanh toán.
     * Quan hệ 1-1: mỗi booking chỉ có đúng một payment.
     * LAZY fetch để tránh N+1 khi chỉ cần thông tin payment.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", foreignKey = @ForeignKey(name = "fk_payments_booking"), nullable = false, unique = true)
    private Booking booking;

    /**
     * Số tiền thanh toán.
     * Tối đa 12 chữ số, 2 chữ số thập phân (VND).
     */
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /**
     * Tài khoản ngân hàng được dùng để thanh toán.
     * LAZY fetch để tránh N+1 khi chỉ cần thông tin payment.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_account_id", foreignKey = @ForeignKey(name = "fk_payments_bank_account"), nullable = false)
    private UserBankAccount bankAccount;

    /**
     * Mã giao dịch do user nhập vào sau khi chuyển khoản.
     */
    @Column(name = "transaction_code", length = 255)
    private String transactionCode;

    /**
     * Trạng thái thanh toán theo {@link PaymentStatus}.
     * Luồng: PENDING → CONFIRMED / FAILED.
     * Lưu dưới dạng chuỗi trong DB.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;
}
