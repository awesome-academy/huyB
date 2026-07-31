package com.sunasterisk.bookingtours.config;

import jakarta.jms.Queue;
import org.apache.activemq.command.ActiveMQQueue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;

/**
 * Cấu hình ActiveMQ cho hệ thống thông báo đặt tour.
 * Sử dụng JMS point-to-point (Queue) để gửi thông báo booking giữa các service.
 * Connection factory và JmsTemplate do Boot auto-config quản lý (CachingConnectionFactory).
 * Trusted packages được cấu hình qua spring.activemq.packages.trusted trong application.properties.
 */
@Configuration
@EnableJms
public class ActiveMQConfig {

    /** Tên queue nhận thông báo khi có booking mới hoặc thay đổi trạng thái booking. */
    public static final String BOOKING_NOTIFICATIONS_QUEUE = "booking.notifications";

    /** Khai báo queue nhận thông báo booking để Spring tự tạo nếu chưa tồn tại trên broker. */
    @Bean
    public Queue bookingNotificationsQueue() {
        return new ActiveMQQueue(BOOKING_NOTIFICATIONS_QUEUE);
    }
}
