package com.sunasterisk.bookingtours.messaging.rabbitmq;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Gửi event tour promotion lên RabbitMQ sau khi transaction commit thành công.
 * Dùng {@code AFTER_COMMIT} để đảm bảo broadcast notification chỉ xảy ra khi
 * tour đã thực sự được lưu ACTIVE trên DB — tránh spam khi transaction rollback.
 */
@Component
@RequiredArgsConstructor
public class TourPromotionDispatcher {

    private final TourPromotionPublisher publisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(TourPromotionMessage message) {
        publisher.publishNewTour(message);
    }
}
