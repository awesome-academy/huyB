package com.sunasterisk.bookingtours.messaging.rabbitmq;

import com.sunasterisk.bookingtours.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publisher phát event khi có tour khuyến mãi mới lên Fanout Exchange {@code tour.promotions}.
 * Được inject vào service khi admin kích hoạt (ACTIVE) một tour.
 */
@Component
@RequiredArgsConstructor
public class TourPromotionPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Publish message tới Fanout Exchange — tất cả queue bind vào exchange
     * ({@code promo.notification} và {@code promo.log}) đều nhận được đồng thời.
     * Routing key truyền vào rỗng vì Fanout bỏ qua routing key.
     */
    public void publishNewTour(TourPromotionMessage message) {
        // Fanout exchange ignores routing key — pass empty string
        rabbitTemplate.convertAndSend(RabbitMQConfig.TOUR_PROMOTIONS_EXCHANGE, "", message);
    }
}
