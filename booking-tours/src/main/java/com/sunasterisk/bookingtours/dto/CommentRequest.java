package com.sunasterisk.bookingtours.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO nhận dữ liệu khi user thêm comment hoặc reply vào một review.
 */
@Getter
@Setter
public class CommentRequest {

    /**
     * Nội dung comment, bắt buộc, tối đa 1000 ký tự.
     */
    @NotBlank(message = "Comment content must not be blank.")
    @Size(max = 1000, message = "Comment content must not exceed 1000 characters.")
    private String content;
}
