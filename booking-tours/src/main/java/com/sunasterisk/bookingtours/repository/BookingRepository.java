package com.sunasterisk.bookingtours.repository;

import com.sunasterisk.bookingtours.entity.Booking;
import com.sunasterisk.bookingtours.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository cho entity {@link Booking}.
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * Đếm số booking theo status.
     */
    long countByStatus(BookingStatus status);

    /**
     * Tổng doanh thu từ các booking theo status.
     */
    @Query("SELECT COALESCE(SUM(b.totalPrice), 0) FROM Booking b WHERE b.status = :status")
    BigDecimal sumTotalPriceByStatus(@Param("status") BookingStatus status);

    /**
     * Đếm số booking được tạo trong khoảng thời gian (dùng cho thống kê booking hôm nay).
     *
     * @param from thời điểm bắt đầu (00:00:00 của ngày)
     * @param to   thời điểm kết thúc (23:59:59 của ngày)
     * @return số lượng booking trong khoảng thời gian đó
     */
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.createdAt BETWEEN :from AND :to")
    long countBookingsBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Tính tổng doanh thu (total_price) của các booking CONFIRMED được tạo trong tháng.
     *
     * @param from   thời điểm đầu tháng
     * @param to     thời điểm cuối tháng
     * @param status trạng thái booking cần tính (truyền {@link BookingStatus#CONFIRMED})
     * @return tổng doanh thu, trả về 0 nếu không có booking nào
     */
    @Query("SELECT COALESCE(SUM(b.totalPrice), 0) FROM Booking b " +
            "WHERE b.status = :status AND b.createdAt BETWEEN :from AND :to")
    BigDecimal sumRevenueByStatusBetween(@Param("status") BookingStatus status,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to);

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
     * Tìm kiếm danh sách tất cả booking (Admin) theo từ khoá (email hoặc destination),
     * lọc theo status (tuỳ chọn), lọc theo ngày khởi hành (tuỳ chọn), phân trang.
     *
     * @param keyword  từ khoá tìm kiếm (null hoặc rỗng → trả về tất cả)
     * @param status   trạng thái cần lọc (null → tất cả)
     * @param fromDate ngày khởi hành cần lọc (null → tất cả)
     * @param toDate   ngày khởi hành cần lọc (null → tất cả)
     * @param pageable thông tin phân trang
     * @return {@code Page<Booking>} với user và tour đã được fetch
     */
    @Query(value = "SELECT b FROM Booking b JOIN FETCH b.user JOIN FETCH b.tour " +
            "WHERE (:keyword IS NULL OR :keyword = '' " +
            "       OR LOWER(b.user.email)       LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "       OR LOWER(b.tour.destination) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:status IS NULL OR b.status = :status) " +
            "AND (:fromDate IS NULL OR b.tour.departureDate >= :fromDate) " +
            "AND (:toDate IS NULL OR b.tour.departureDate <= :toDate) " +
            "ORDER BY b.createdAt DESC",
            countQuery = "SELECT COUNT(b) FROM Booking b JOIN b.tour " +
                    "WHERE (:keyword IS NULL OR :keyword = '' " +
                    "       OR LOWER(b.user.email)       LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                    "       OR LOWER(b.tour.destination) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                    "AND (:status IS NULL OR b.status = :status) " +
                    "AND (:fromDate IS NULL OR b.tour.departureDate >= :fromDate) " +
                    "AND (:toDate IS NULL OR b.tour.departureDate <= :toDate)")
    Page<Booking> searchByKeywordAndStatusAndDepartureDate(@Param("keyword") String keyword,
                                                           @Param("status") BookingStatus status,
                                                           @Param("fromDate") LocalDate fromDate,
                                                           @Param("toDate") LocalDate toDate,
                                                           Pageable pageable);
}
