package com.sunasterisk.bookingtours.messaging.activemq;

import com.sunasterisk.bookingtours.entity.Notification.NotificationType;
import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingNotificationMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private NotificationType type;
    private String title;
    private String message;
}
