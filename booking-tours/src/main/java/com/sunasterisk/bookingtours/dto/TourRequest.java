package com.sunasterisk.bookingtours.dto;

import com.sunasterisk.bookingtours.entity.TourStatus;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO dùng để nhận dữ liệu từ form tạo mới / chỉnh sửa tour.
 * Không bao gồm các trường tính toán như {@code avgRating}.
 */
@Getter
@Setter
public class TourRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    /**
     * Giá tour, phải lớn hơn 0.
     */
    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    private BigDecimal price;

    /**
     * Số ngày của tour, phải >= 1.
     */
    @NotNull(message = "Duration is required")
    @Min(value = 1, message = "Duration must be at least 1 day")
    private Integer durationDays;

    /**
     * Số người tối đa, phải >= 1.
     */
    @NotNull(message = "Max participants is required")
    @Min(value = 1, message = "Max participants must be at least 1")
    private Integer maxParticipants;

    @NotBlank(message = "Departure location is required")
    @Size(max = 255, message = "Departure location must not exceed 255 characters")
    private String departureLocation;

    @NotBlank(message = "Destination is required")
    @Size(max = 255, message = "Destination must not exceed 255 characters")
    private String destination;

    @NotNull(message = "Departure date is required")
    private LocalDate departureDate;

    /**
     * URL ảnh thumbnail hiện tại (lưu lại khi edit mà không upload file mới).
     * Được populate từ hidden field trong form.
     */
    private String thumbnailUrl;

    /**
     * ID của danh mục được chọn, bắt buộc.
     */
    @NotNull(message = "Category is required")
    private Long categoryId;

    /**
     * Trạng thái tour, bắt buộc.
     */
    @NotNull(message = "Status is required")
    private TourStatus status;
}
