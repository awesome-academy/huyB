package com.sunasterisk.bookingtours.service;

import com.sunasterisk.bookingtours.dto.NotificationDto;
import com.sunasterisk.bookingtours.entity.Notification.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service quản lý notification: tạo, truy vấn, và cập nhật trạng thái đã đọc.
 */
public interface NotificationService {

    /**
     * Tạo và lưu một notification cho user — được gọi từ {@code BookingNotificationConsumer} sau khi nhận message từ ActiveMQ.
     */
    void saveNotification(Long userId, NotificationType type, String title, String message);

    /**
     * Trả về số notification chưa đọc — dùng để hiển thị badge trên UI.
     */
    long getUnreadCount(Long userId);

    /**
     * Lấy danh sách notification của user có phân trang, sắp xếp mới nhất trước.
     */
    Page<NotificationDto> getNotifications(Long userId, Pageable pageable);

    /**
     * Đánh dấu toàn bộ notification chưa đọc của user thành đã đọc.
     */
    void markAllRead(Long userId);

    /**
     * Gửi TOUR_PROMOTION notification tới tất cả user active (batch insert).
     */
    void broadcastTourPromotion(Long tourId, String tourTitle);
}
