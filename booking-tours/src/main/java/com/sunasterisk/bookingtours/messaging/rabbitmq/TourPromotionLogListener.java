package com.sunasterisk.bookingtours.messaging.rabbitmq;

import com.sunasterisk.bookingtours.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listener ghi log khi có tour khuyến mãi mới được publish.
 * Lắng nghe queue {@code tour.promo.log.queue} — một trong hai subscriber
 * nhận broadcast từ Fanout Exchange {@code tour.promotions}.
 * Mục đích: audit trail, theo dõi lịch sử phát hành tour khuyến mãi.
 */
@Slf4j
@Component
public class TourPromotionLogListener {

    /** Ghi log INFO mỗi khi nhận được event tour khuyến mãi mới. */
    @RabbitListener(queues = RabbitMQConfig.PROMO_LOG_QUEUE)
    public void onMessage(TourPromotionMessage message) {
        try {
            log.info("New ACTIVE tour published: id={}, title={}", message.getTourId(), message.getTourTitle());
        } catch (Exception e) {
            log.error("Failed to log tour promotion event: tourId={} — routing to DLQ", message.getTourId(), e);
            throw new AmqpRejectAndDontRequeueException(e);
        }
    }
}
