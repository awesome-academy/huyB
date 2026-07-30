package com.sunasterisk.bookingtours.messaging.activemq;

import com.sunasterisk.bookingtours.config.ActiveMQConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

/**
 * Producer gửi thông báo booking vào queue {@code booking.notifications} trên ActiveMQ.
 * Được inject vào các service cần phát sinh notification (đặt tour, huỷ, xác nhận, v.v.).
 */
@Component
@RequiredArgsConstructor
public class BookingNotificationProducer {

    private final JmsTemplate jmsTemplate;

    /**
     * Serialize message thành JMS ObjectMessage và đẩy vào queue bất đồng bộ.
     * Gọi từ business service sau khi trạng thái booking thay đổi.
     */
    public void sendNotification(BookingNotificationMessage message) {
        jmsTemplate.convertAndSend(ActiveMQConfig.BOOKING_NOTIFICATIONS_QUEUE, message);
    }
}
