package com.sunasterisk.bookingtours.repository;

import com.sunasterisk.bookingtours.entity.Booking;
import com.sunasterisk.bookingtours.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repository cho entity {@link Booking}.
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * Kiểm tra mã booking đã tồn tại chưa (dùng khi generate booking_code).
     *
     * @param bookingCode mã booking cần kiểm tra
     * @return {@code true} nếu đã tồn tại
     */
    boolean existsByBookingCode(String bookingCode);

    /**
     * Tìm booking theo id với tour và user đã được fetch (dùng cho trang chi tiết booking).
     *
     * @param id id của booking
     * @return {@code Optional<Booking>} với tour và user đã được JOIN FETCH
     */
    @Query("SELECT b FROM Booking b JOIN FETCH b.tour JOIN FETCH b.user WHERE b.id = :id")
    Optional<Booking> findByIdWithTourAndUser(@Param("id") Long id);

    /**
     * Tìm booking theo mã booking và user_id — dùng để xem chi tiết booking của chính user đó.
     *
     * @param bookingCode mã booking
     * @param userId      id của user sở hữu booking
     * @return {@code Optional<Booking>} với tour và user đã được fetch
     */
    @Query("SELECT b FROM Booking b JOIN FETCH b.tour JOIN FETCH b.user " +
           "WHERE b.bookingCode = :bookingCode AND b.user.id = :userId")
    Optional<Booking> findByBookingCodeAndUserId(@Param("bookingCode") String bookingCode,
                                                 @Param("userId") Long userId);

    /**
     * Lấy danh sách booking của một user, lọc theo status (tuỳ chọn), phân trang.
     * Dùng cho trang lịch sử booking của User.
     *
     * @param userId   id của user
     * @param status   trạng thái cần lọc (null → tất cả)
     * @param pageable thông tin phân trang
     * @return {@code Page<Booking>} với tour đã được fetch
     */
    @Query(value = "SELECT b FROM Booking b JOIN FETCH b.tour " +
                   "WHERE b.user.id = :userId " +
                   "AND (:status IS NULL OR b.status = :status) " +
                   "ORDER BY b.createdAt DESC",
           countQuery = "SELECT COUNT(b) FROM Booking b " +
                        "WHERE b.user.id = :userId " +
                        "AND (:status IS NULL OR b.status = :status)")
    Page<Booking> findByUserIdAndStatus(@Param("userId") Long userId,
                                        @Param("status") BookingStatus status,
                                        Pageable pageable);

    /**
     * Lấy danh sách tất cả booking (Admin), lọc theo status (tuỳ chọn), phân trang.
     *
     * @param status   trạng thái cần lọc (null → tất cả)
     * @param pageable thông tin phân trang
     * @return {@code Page<Booking>} với user và tour đã được fetch
     */
    @Query(value = "SELECT b FROM Booking b JOIN FETCH b.user JOIN FETCH b.tour " +
                   "WHERE (:status IS NULL OR b.status = :status) " +
                   "ORDER BY b.createdAt DESC",
           countQuery = "SELECT COUNT(b) FROM Booking b " +
                        "WHERE (:status IS NULL OR b.status = :status)")
    Page<Booking> findAllByStatus(@Param("status") BookingStatus status, Pageable pageable);
}
