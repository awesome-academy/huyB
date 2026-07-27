package com.sunasterisk.bookingtours.entity;

/**
 * Trạng thái của một giao dịch thanh toán.
 *
 * <ul>
 *   <li>{@link #PENDING}   – Chờ admin xác nhận (user vừa nhập mã giao dịch)</li>
 *   <li>{@link #CONFIRMED} – Admin đã xác nhận thanh toán hợp lệ</li>
 *   <li>{@link #FAILED}    – Thanh toán không hợp lệ hoặc bị từ chối</li>
 * </ul>
 */
public enum PaymentStatus {
    PENDING,
    CONFIRMED,
    FAILED
}
