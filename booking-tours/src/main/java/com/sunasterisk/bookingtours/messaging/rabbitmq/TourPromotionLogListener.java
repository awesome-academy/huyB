package com.sunasterisk.bookingtours.messaging.rabbitmq;

import com.sunasterisk.bookingtours.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TourPromotionLogListener {

    @RabbitListener(queues = RabbitMQConfig.PROMO_LOG_QUEUE)
    public void onMessage(TourPromotionMessage message) {
        log.info("New ACTIVE tour published: id={}, title={}", message.getTourId(), message.getTourTitle());
    }
}
