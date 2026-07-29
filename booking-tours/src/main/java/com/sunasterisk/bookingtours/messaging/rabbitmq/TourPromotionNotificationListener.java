package com.sunasterisk.bookingtours.messaging.rabbitmq;

import com.sunasterisk.bookingtours.config.RabbitMQConfig;
import com.sunasterisk.bookingtours.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TourPromotionNotificationListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitMQConfig.PROMO_NOTIFICATION_QUEUE)
    public void onMessage(TourPromotionMessage message) {
        notificationService.broadcastTourPromotion(message.getTourId(), message.getTourTitle());
    }
}
