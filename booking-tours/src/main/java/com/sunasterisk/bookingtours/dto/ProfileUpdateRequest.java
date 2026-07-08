package com.sunasterisk.bookingtours.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileUpdateRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 255, message = "Full name must not exceed 255 characters")
    private String fullName;

    @Pattern(
            regexp = "^$|^[+]?[0-9\\s\\-().]{7,20}$",
            message = "Invalid phone number format"
    )
    private String phone;

    // Chỉ chấp nhận https URL — chặn javascript:/data: URI được render vào th:src
    // trên trang profile và trang Admin users. Yêu cầu https (không phải http) để
    // khớp với CSP img-src ('self' data: https:) — ảnh http sẽ bị browser chặn.
    @Pattern(
            regexp = "^$|^https://.+$",
            message = "Avatar URL must start with https://"
    )
    @Size(max = 2048, message = "Avatar URL must not exceed 2048 characters")
    private String avatarUrl;
}
