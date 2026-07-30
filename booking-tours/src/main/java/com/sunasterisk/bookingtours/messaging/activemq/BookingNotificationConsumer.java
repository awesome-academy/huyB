package com.sunasterisk.bookingtours.messaging.activemq;

import com.sunasterisk.bookingtours.config.ActiveMQConfig;
import com.sunasterisk.bookingtours.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * Consumer lắng nghe queue {@code booking.notifications} trên ActiveMQ.
 * Mỗi message nhận được sẽ được lưu vào DB thông qua {@link NotificationService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingNotificationConsumer {

    private final NotificationService notificationService;

    /**
     * Xử lý message booking notification từ queue.
     * Re-throw exception để JMS đánh dấu nack, kích hoạt redelivery hoặc đẩy sang DLQ
     * nếu vượt quá số lần thử lại cho phép.
     */
    @JmsListener(destination = ActiveMQConfig.BOOKING_NOTIFICATIONS_QUEUE)
    public void onMessage(BookingNotificationMessage message) {
        try {
            notificationService.saveNotification(
                    message.getUserId(),
                    message.getType(),
                    message.getTitle(),
                    message.getMessage()
            );
        } catch (Exception e) {
            log.error("Failed to persist booking notification: userId={}, type={} — {}",
                    message.getUserId(), message.getType(), e.getMessage(), e);
            throw e; // re-throw → JMS nack → redelivery / DLQ
        }
    }
}
