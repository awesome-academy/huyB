package com.sunasterisk.bookingtours.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO chứa dữ liệu từ form đặt tour của User.
 */
@Getter
@Setter
public class BookingRequest {

    /**
     * ID của tour muốn đặt.
     */
    @NotNull(message = "Tour ID is required")
    private Long tourId;

    /**
     * Số lượng người tham gia, tối thiểu 1, tối đa 100.
     */
    @NotNull(message = "Number of participants is required")
    @Min(value = 1, message = "At least 1 participant is required")
    @Max(value = 100, message = "Maximum 100 participants per booking")
    private Integer participants;

    /**
     * Ghi chú tuỳ chọn của user.
     */
    private String note;
}
