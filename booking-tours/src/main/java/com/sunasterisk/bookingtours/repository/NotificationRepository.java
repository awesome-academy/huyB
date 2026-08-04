package com.sunasterisk.bookingtours.repository;

import com.sunasterisk.bookingtours.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Repository truy cập bảng notification, hỗ trợ phân trang và quản lý trạng thái đã đọc. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Lấy danh sách notification của user, sắp xếp mới nhất trước, có phân trang. */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Đếm số notification chưa đọc của user — dùng để hiển thị badge trên UI. */
    long countByUserIdAndIsReadFalse(Long userId);

    /**
     * Đánh dấu tất cả notification chưa đọc của user thành đã đọc bằng bulk UPDATE.
     * {@code clearAutomatically = true} xóa persistence context sau khi UPDATE
     * để tránh stale cache khi đọc lại entity trong cùng transaction.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    void markAllReadByUserId(@Param("userId") Long userId);

    /**
     * Insert TOUR_PROMOTION notification cho tất cả user active bằng một statement INSERT ... SELECT.
     * Không load id nào lên app, không cần lấy generated key — tránh N round-trip và giới hạn bộ nhớ
     * khi số lượng user lớn. No-op tự nhiên nếu không có user active.
     */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO notifications (user_id, type, title, message, is_read, created_at, updated_at)
            SELECT u.id, 'TOUR_PROMOTION', :title, :message, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
            FROM users u WHERE u.is_active = 1
            """, nativeQuery = true)
    void insertPromotionForAllActiveUsers(@Param("title") String title, @Param("message") String message);
}
