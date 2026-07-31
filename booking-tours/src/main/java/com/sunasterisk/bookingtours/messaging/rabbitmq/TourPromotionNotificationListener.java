package com.sunasterisk.bookingtours.messaging.rabbitmq;

import com.sunasterisk.bookingtours.config.RabbitMQConfig;
import com.sunasterisk.bookingtours.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listener gửi thông báo khuyến mãi tour tới người dùng.
 * Lắng nghe queue {@code tour.promo.notification.queue} — một trong hai subscriber
 * nhận broadcast từ Fanout Exchange {@code tour.promotions}.
 * Mục đích: tạo notification trong DB để hiển thị cho tất cả người dùng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TourPromotionNotificationListener {

    private final NotificationService notificationService;

    /**
     * Gọi {@link NotificationService#broadcastTourPromotion} để tạo notification cho toàn bộ người dùng.
     * Khi xảy ra lỗi, throw {@link AmqpRejectAndDontRequeueException} để message được chuyển sang DLQ
     * thay vì requeue liên tục gây hot loop.
     */
    @RabbitListener(queues = RabbitMQConfig.PROMO_NOTIFICATION_QUEUE)
    public void onMessage(TourPromotionMessage message) {
        try {
            notificationService.broadcastTourPromotion(message.getTourId(), message.getTourTitle());
        } catch (Exception e) {
            log.error("Failed to broadcast tour promotion notification: tourId={}, title={} — routing to DLQ",
                    message.getTourId(), message.getTourTitle(), e);
            throw new AmqpRejectAndDontRequeueException(e);
        }
    }
}
