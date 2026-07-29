package com.sunasterisk.bookingtours.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitMQConfig {

    public static final String TOUR_PROMOTIONS_EXCHANGE   = "tour.promotions";
    public static final String PROMO_NOTIFICATION_QUEUE   = "tour.promo.notification.queue";
    public static final String PROMO_LOG_QUEUE            = "tour.promo.log.queue";

    @Bean
    public FanoutExchange tourPromotionsExchange() {
        return new FanoutExchange(TOUR_PROMOTIONS_EXCHANGE);
    }

    @Bean
    public Queue promoNotificationQueue() {
        return new Queue(PROMO_NOTIFICATION_QUEUE);
    }

    @Bean
    public Queue promoLogQueue() {
        return new Queue(PROMO_LOG_QUEUE);
    }

    @Bean
    public Binding promoNotificationBinding(Queue promoNotificationQueue, FanoutExchange tourPromotionsExchange) {
        return BindingBuilder.bind(promoNotificationQueue).to(tourPromotionsExchange);
    }

    @Bean
    public Binding promoLogBinding(Queue promoLogQueue, FanoutExchange tourPromotionsExchange) {
        return BindingBuilder.bind(promoLogQueue).to(tourPromotionsExchange);
    }

    @Bean
    public Jackson2JsonMessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
