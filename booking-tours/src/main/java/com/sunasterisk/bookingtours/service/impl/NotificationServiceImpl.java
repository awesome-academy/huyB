package com.sunasterisk.bookingtours.service.impl;

import com.sunasterisk.bookingtours.dto.NotificationDto;
import com.sunasterisk.bookingtours.entity.Notification;
import com.sunasterisk.bookingtours.entity.Notification.NotificationType;
import com.sunasterisk.bookingtours.repository.NotificationRepository;
import com.sunasterisk.bookingtours.repository.UserRepository;
import com.sunasterisk.bookingtours.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

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

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationDto> getNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationDto::from);
    }

    @Override
    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllReadByUserId(userId);
    }

    @Override
    @Transactional
    public void broadcastTourPromotion(Long tourId, String tourTitle) {
        List<Long> activeUserIds = userRepository.findAllActiveUserIds();
        if (activeUserIds.isEmpty()) {
            return;
        }

        String title   = "Tour mới: " + tourTitle;
        String message = "Tour \"" + tourTitle + "\" vừa được kích hoạt. Đặt ngay!";

        List<Notification> notifications = activeUserIds.stream()
                .map(uid -> Notification.builder()
                        .userId(uid)
                        .type(NotificationType.TOUR_PROMOTION)
                        .title(title)
                        .message(message)
                        .isRead(false)
                        .build())
                .toList();

        notificationRepository.saveAll(notifications);
    }
}
