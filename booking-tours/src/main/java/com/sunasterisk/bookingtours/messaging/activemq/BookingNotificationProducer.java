package com.sunasterisk.bookingtours.messaging.activemq;

import com.sunasterisk.bookingtours.config.ActiveMQConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookingNotificationProducer {

    private final JmsTemplate jmsTemplate;

    public void sendNotification(BookingNotificationMessage message) {
        jmsTemplate.convertAndSend(ActiveMQConfig.BOOKING_NOTIFICATIONS_QUEUE, message);
    }
}
