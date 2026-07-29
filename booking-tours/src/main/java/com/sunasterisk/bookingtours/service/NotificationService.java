package com.sunasterisk.bookingtours.service;

import com.sunasterisk.bookingtours.dto.NotificationDto;
import com.sunasterisk.bookingtours.entity.Notification.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    void saveNotification(Long userId, NotificationType type, String title, String message);

    long getUnreadCount(Long userId);

    Page<NotificationDto> getNotifications(Long userId, Pageable pageable);

    void markAllRead(Long userId);

    /** Gửi TOUR_PROMOTION notification tới tất cả user active (batch insert). */
    void broadcastTourPromotion(Long tourId, String tourTitle);
}
