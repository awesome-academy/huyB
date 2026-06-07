package com.sunasterisk.bookingtours.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO nhận dữ liệu khi user rating một tour (1–5 sao).
 */
@Getter
@Setter
public class RatingRequest {

    /**
     * Điểm đánh giá từ 1 đến 5, bắt buộc.
     */
    @NotNull(message = "Score is required.")
    @Min(value = 1, message = "Score must be at least 1.")
    @Max(value = 5, message = "Score must be at most 5.")
    private Short score;
}
