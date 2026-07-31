package com.sunasterisk.bookingtours.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình RabbitMQ cho hệ thống thông báo khuyến mãi tour.
 * Sử dụng mô hình Fanout Exchange: một message publish tới exchange
 * sẽ được broadcast đồng thời tới tất cả queue đang bind vào exchange đó.
 */
@Configuration
@EnableRabbit
public class RabbitMQConfig {

    /** Tên exchange fanout nhận event khi có khuyến mãi tour mới. */
    public static final String TOUR_PROMOTIONS_EXCHANGE   = "tour.promotions";

    /** Queue gửi thông báo khuyến mãi tới người dùng (email, push notification, v.v.). */
    public static final String PROMO_NOTIFICATION_QUEUE   = "tour.promo.notification.queue";

    /** Queue ghi log khuyến mãi để theo dõi lịch sử và audit. */
    public static final String PROMO_LOG_QUEUE            = "tour.promo.log.queue";

    /** Dead Letter Exchange — nhận message bị reject sau khi xử lý thất bại. */
    public static final String PROMO_DLX                  = "tour.promotions.dlx";

    /** DLQ cho notification — giữ message lỗi để inspect/replay thủ công. */
    public static final String PROMO_NOTIFICATION_DLQ     = "tour.promo.notification.dlq";

    /** DLQ cho log — giữ message lỗi để audit. */
    public static final String PROMO_LOG_DLQ              = "tour.promo.log.dlq";

    /**
     * Khai báo Fanout Exchange cho event khuyến mãi.
     * Fanout bỏ qua routing key — mọi queue bind vào đều nhận được message.
     */
    @Bean
    public FanoutExchange tourPromotionsExchange() {
        return new FanoutExchange(TOUR_PROMOTIONS_EXCHANGE);
    }

    /**
     * Dead Letter Exchange (Direct) — điểm đến cho message bị reject.
     * Dùng Direct thay vì Fanout để mỗi DLQ nhận đúng message của queue tương ứng.
     */
    @Bean
    public DirectExchange promoDlx() {
        return new DirectExchange(PROMO_DLX);
    }

    /** DLQ cho notification — durable, không TTL, giữ để inspect/replay. */
    @Bean
    public Queue promoNotificationDlq() {
        return QueueBuilder.durable(PROMO_NOTIFICATION_DLQ).build();
    }

    /** DLQ cho log. */
    @Bean
    public Queue promoLogDlq() {
        return QueueBuilder.durable(PROMO_LOG_DLQ).build();
    }

    /**
     * Queue xử lý thông báo khuyến mãi tới người dùng, durable.
     * Khai báo DLX để message bị reject (AmqpRejectAndDontRequeueException) được
     * chuyển sang DLQ thay vì requeue vô hạn.
     */
    @Bean
    public Queue promoNotificationQueue() {
        return QueueBuilder.durable(PROMO_NOTIFICATION_QUEUE)
                .deadLetterExchange(PROMO_DLX)
                .deadLetterRoutingKey(PROMO_NOTIFICATION_DLQ)
                .build();
    }

    /** Queue ghi log khuyến mãi, durable, với DLX để tránh requeue loop. */
    @Bean
    public Queue promoLogQueue() {
        return QueueBuilder.durable(PROMO_LOG_QUEUE)
                .deadLetterExchange(PROMO_DLX)
                .deadLetterRoutingKey(PROMO_LOG_DLQ)
                .build();
    }

    /** Bind promoNotificationQueue vào exchange để nhận broadcast khuyến mãi. */
    @Bean
    public Binding promoNotificationBinding(Queue promoNotificationQueue, FanoutExchange tourPromotionsExchange) {
        return BindingBuilder.bind(promoNotificationQueue).to(tourPromotionsExchange);
    }

    /** Bind promoLogQueue vào exchange để nhận broadcast khuyến mãi song song với notification. */
    @Bean
    public Binding promoLogBinding(Queue promoLogQueue, FanoutExchange tourPromotionsExchange) {
        return BindingBuilder.bind(promoLogQueue).to(tourPromotionsExchange);
    }

    /** Bind DLQ notification vào DLX với routing key tương ứng. */
    @Bean
    public Binding promoNotificationDlqBinding(Queue promoNotificationDlq, DirectExchange promoDlx) {
        return BindingBuilder.bind(promoNotificationDlq).to(promoDlx).with(PROMO_NOTIFICATION_DLQ);
    }

    /** Bind DLQ log vào DLX với routing key tương ứng. */
    @Bean
    public Binding promoLogDlqBinding(Queue promoLogDlq, DirectExchange promoDlx) {
        return BindingBuilder.bind(promoLogDlq).to(promoDlx).with(PROMO_LOG_DLQ);
    }

    /**
     * Converter serialize/deserialize message thành JSON thay vì Java binary.
     * Giúp message dễ đọc trên RabbitMQ Management UI và tương thích với các consumer khác ngôn ngữ.
     */
    @Bean
    public JacksonJsonMessageConverter jacksonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * RabbitTemplate là entry point chính để publish message tới exchange.
     * Gắn Jackson converter để tự động chuyển object thành JSON khi gửi.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         JacksonJsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
