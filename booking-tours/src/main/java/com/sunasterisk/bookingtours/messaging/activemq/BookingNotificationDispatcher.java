package com.sunasterisk.bookingtours.messaging.activemq;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Gửi thông báo booking lên ActiveMQ sau khi transaction commit thành công.
 * Dùng {@code AFTER_COMMIT} để đảm bảo message chỉ đến broker khi DB đã chắc chắn
 * lưu xong — tránh notification "xác nhận" nhưng DB thực tế rollback.
 */
@Component
@RequiredArgsConstructor
public class BookingNotificationDispatcher {

    private final BookingNotificationProducer producer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(BookingNotificationMessage message) {
        producer.sendNotification(message);
    }
}
