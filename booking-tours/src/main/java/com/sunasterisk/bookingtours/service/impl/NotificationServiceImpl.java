package com.sunasterisk.bookingtours.service.impl;

import com.sunasterisk.bookingtours.dto.NotificationDto;
import com.sunasterisk.bookingtours.entity.Notification;
import com.sunasterisk.bookingtours.entity.Notification.NotificationType;
import com.sunasterisk.bookingtours.repository.NotificationRepository;
import com.sunasterisk.bookingtours.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Triển khai {@link NotificationService}, xử lý toàn bộ logic tạo và quản lý notification. */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    /** Tạo notification mới với trạng thái chưa đọc và lưu vào DB. */
    @Override
    @Transactional
    public void saveNotification(Long userId, NotificationType type, String title, String message) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .isRead(false)
                .build();
        notificationRepository.save(notification);
    }

    /** {@code readOnly = true} giúp Hibernate bỏ qua dirty checking, tăng hiệu năng truy vấn. */
    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    /** Map entity sang DTO ngay tại tầng persistence để tránh lazy-loading ngoài transaction. */
    @Override
    @Transactional(readOnly = true)
    public Page<NotificationDto> getNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationDto::from);
    }

    /** Dùng bulk UPDATE thay vì load từng entity để tránh N+1 khi user có nhiều notification. */
    @Override
    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllReadByUserId(userId);
    }

    /**
     * Insert TOUR_PROMOTION notification cho tất cả user active bằng một native INSERT ... SELECT.
     * DB làm toàn bộ việc — không load id nào lên app, không giữ entity trong persistence context.
     */
    @Override
    @Transactional
    public void broadcastTourPromotion(Long tourId, String tourTitle) {
        String title   = "Tour mới: " + tourTitle;
        String message = "Tour \"" + tourTitle + "\" vừa được kích hoạt. Đặt ngay!";
        notificationRepository.insertPromotionForAllActiveUsers(title, message);
    }
}
