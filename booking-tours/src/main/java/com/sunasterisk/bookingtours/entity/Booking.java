package com.sunasterisk.bookingtours.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Entity đại diện cho một lượt đặt tour (booking) trong hệ thống.
 * Kế thừa {@link BaseEntity} để tự động quản lý created_at / updated_at.
 *
 * <p>Quan hệ:
 * <ul>
 *   <li>N booking → 1 {@link User} (người đặt tour)</li>
 *   <li>N booking → 1 {@link Tour} (tour được đặt)</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"user", "tour"})
@Entity
@Table(name = "bookings")
public class Booking extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Mã booking duy nhất, format: {@code BK-YYYYMMDD-XXXX}.
     * Được generate trong service trước khi lưu DB.
     */
    @Column(name = "booking_code", length = 20, nullable = false, unique = true)
    private String bookingCode;

    /**
     * User đã đặt tour.
     * LAZY fetch để tránh N+1 khi chỉ cần thông tin booking.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_bookings_user"), nullable = false)
    private User user;

    /**
     * Tour được đặt.
     * LAZY fetch để tránh N+1 khi chỉ cần thông tin booking.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tour_id", foreignKey = @ForeignKey(name = "fk_bookings_tour"), nullable = false)
    private Tour tour;

    /**
     * Số lượng người tham gia, tối thiểu 1.
     */
    @Column(name = "participants", nullable = false)
    private Integer participants;

    /**
     * Tổng tiền thanh toán = {@code tour.price × participants}.
     * Tối đa 12 chữ số, 2 chữ số thập phân (VND).
     */
    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    /**
     * Trạng thái booking theo {@link BookingStatus}.
     * Luồng: PENDING → CONFIRMED / CANCELLED → COMPLETED.
     * Lưu dưới dạng chuỗi trong DB.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private BookingStatus status = BookingStatus.PENDING;

    /**
     * Ghi chú thêm của user khi đặt tour (tuỳ chọn).
     */
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
