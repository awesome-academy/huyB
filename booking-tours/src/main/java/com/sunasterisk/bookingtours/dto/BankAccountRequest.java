package com.sunasterisk.bookingtours.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BankAccountRequest {

    @NotBlank(message = "Bank name is required")
    @Size(max = 100, message = "Bank name must not exceed 100 characters")
    private String bankName;

    @NotBlank(message = "Account number is required")
    @Pattern(
            regexp = "^[0-9]{6,20}$",
            message = "Account number must contain 6–20 digits"
    )
    private String accountNumber;

    @NotBlank(message = "Account holder name is required")
    @Size(max = 255, message = "Account holder name must not exceed 255 characters")
    private String accountHolder;

    private boolean isDefault;
}
