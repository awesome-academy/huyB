package com.sunasterisk.bookingtours.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO cho form thanh toán booking (task 7.2).
 * User chọn tài khoản ngân hàng và nhập mã giao dịch.
 */
@Data
public class PaymentRequest {

    /**
     * ID tài khoản ngân hàng của user được chọn để thanh toán.
     */
    @NotNull(message = "Please select a bank account.")
    private Long bankAccountId;

    /**
     * Mã giao dịch do user nhập sau khi chuyển khoản.
     */
    @NotBlank(message = "Transaction code is required.")
    @Size(max = 255, message = "Transaction code must not exceed 255 characters.")
    private String transactionCode;
}
