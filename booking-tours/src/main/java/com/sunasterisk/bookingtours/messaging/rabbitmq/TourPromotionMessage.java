package com.sunasterisk.bookingtours.messaging.rabbitmq;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TourPromotionMessage {

    private Long tourId;
    private String tourTitle;
}
