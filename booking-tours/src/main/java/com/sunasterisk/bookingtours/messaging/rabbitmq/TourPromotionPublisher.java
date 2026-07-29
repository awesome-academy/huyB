package com.sunasterisk.bookingtours.messaging.rabbitmq;

import com.sunasterisk.bookingtours.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TourPromotionPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishNewTour(TourPromotionMessage message) {
        // Fanout exchange ignores routing key — pass empty string
        rabbitTemplate.convertAndSend(RabbitMQConfig.TOUR_PROMOTIONS_EXCHANGE, "", message);
    }
}
