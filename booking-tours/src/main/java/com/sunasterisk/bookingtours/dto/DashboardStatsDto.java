package com.sunasterisk.bookingtours.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO chứa các số liệu thống kê cho Admin Dashboard.
 *
 * <ul>
 *   <li>{@code totalUsers}    — tổng số user trong hệ thống</li>
 *   <li>{@code totalTours}    — tổng số tour trong hệ thống</li>
 *   <li>{@code bookingsToday} — số booking được tạo trong ngày hôm nay</li>
 *   <li>{@code revenueThisMonth} — doanh thu tháng hiện tại (từ booking CONFIRMED)</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatsDto {

    /**
     * Tổng số user đã đăng ký.
     */
    private long totalUsers;

    /**
     * Tổng số tour trong hệ thống.
     */
    private long totalTours;

    /**
     * Số booking được tạo hôm nay.
     */
    private long bookingsToday;

    /**
     * Doanh thu tháng hiện tại (chỉ tính booking CONFIRMED).
     */
    private BigDecimal revenueThisMonth;
}
