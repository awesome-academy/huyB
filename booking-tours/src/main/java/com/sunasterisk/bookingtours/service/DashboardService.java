package com.sunasterisk.bookingtours.service;

import com.sunasterisk.bookingtours.dto.DashboardStatsDto;

/**
 * Service cung cấp dữ liệu thống kê cho Admin Dashboard.
 */
public interface DashboardService {

    /**
     * Thu thập và trả về các số liệu thống kê tổng quan:
     * tổng user, tổng tour, booking hôm nay và doanh thu tháng hiện tại.
     *
     * @return {@link DashboardStatsDto} chứa các chỉ số thống kê
     */
    DashboardStatsDto getStats();
}
