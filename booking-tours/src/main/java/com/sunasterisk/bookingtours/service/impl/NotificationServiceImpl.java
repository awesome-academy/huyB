package com.sunasterisk.bookingtours.service.impl;

import com.sunasterisk.bookingtours.dto.NotificationDto;
import com.sunasterisk.bookingtours.entity.Notification;
import com.sunasterisk.bookingtours.entity.Notification.NotificationType;
import com.sunasterisk.bookingtours.repository.NotificationRepository;
import com.sunasterisk.bookingtours.repository.UserRepository;
import com.sunasterisk.bookingtours.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.util.Map;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Triển khai {@link NotificationService}, xử lý toàn bộ logic tạo và quản lý notification. */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Tạo notification mới và push real-time qua WebSocket đến user.
     * Chạy trong notif-async thread pool để không block HTTP response của caller.
     * Push xảy ra sau save; nếu user không kết nối WebSocket, message bị bỏ qua (không lỗi).
     */
    @Override
    @Async("notificationExecutor")
    @Transactional
    public void saveNotification(Long userId, NotificationType type, String title, String message) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .isRead(false)
                .build();
        Notification saved = notificationRepository.save(notification);

        // Push real-time tới user đang kết nối WebSocket — principal name là email
        userRepository.findById(userId).ifPresent(user -> {
            try {
                messagingTemplate.convertAndSendToUser(
                        user.getEmail(),
                        "/queue/notifications",
                        NotificationDto.from(saved)
                );
            } catch (Exception e) {
                log.warn("WebSocket push failed for userId={}: {}", userId, e.getMessage());
            }
        });
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
     * Chạy async để không block RabbitMQ listener thread.
     */
    @Override
    @Async("notificationExecutor")
    @Transactional
    public void broadcastTourPromotion(Long tourId, String tourTitle) {
        String title   = "Tour mới: " + tourTitle;
        String message = "Tour \"" + tourTitle + "\" vừa được kích hoạt. Đặt ngay!";

        // 1. Ghi DB cho tất cả user active (bulk INSERT, không cần load entity lên memory)
        notificationRepository.insertPromotionForAllActiveUsers(title, message);

        // 2. Push real-time qua broadcast topic — một lần gửi, mọi client đang kết nối đều nhận.
        //    Dùng /topic/ thay vì N lần convertAndSendToUser() vì đây là notification kiểu broadcast.
        //    User không kết nối WebSocket sẽ thấy badge cập nhật ở lần load trang tiếp theo (từ DB).
        try {
            Map<String, Object> payload = Map.of(
                    "type", "TOUR_PROMOTION",
                    "title", title,
                    "message", message
            );
            // Cast tường minh để tránh ambiguous overload giữa convertAndSend(dest, Object)
            // và convertAndSend(Object, Map<String,Object>)
            messagingTemplate.convertAndSend("/topic/promotions", (Object) payload);
        } catch (Exception e) {
            log.warn("WebSocket broadcast failed for tour promotion tourId={}: {}", tourId, e.getMessage());
        }
    }
}
