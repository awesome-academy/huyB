package com.sunasterisk.bookingtours.config;

import jakarta.jms.Queue;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQQueue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.core.JmsTemplate;

import java.util.List;

@Configuration
@EnableJms
public class ActiveMQConfig {

    public static final String BOOKING_NOTIFICATIONS_QUEUE = "booking.notifications";

    @Value("${spring.activemq.broker-url}")
    private String brokerUrl;

    @Bean
    public ActiveMQConnectionFactory activeMQConnectionFactory() {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        factory.setTrustedPackages(List.of(
                "com.sunasterisk.bookingtours.messaging.activemq",
                "com.sunasterisk.bookingtours.entity"
        ));
        return factory;
    }

    @Bean
    public JmsTemplate jmsTemplate(ActiveMQConnectionFactory connectionFactory) {
        return new JmsTemplate(connectionFactory);
    }

    @Bean
    public Queue bookingNotificationsQueue() {
        return new ActiveMQQueue(BOOKING_NOTIFICATIONS_QUEUE);
    }
}
