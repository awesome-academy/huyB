package com.sunasterisk.bookingtours.service.impl;

import com.sunasterisk.bookingtours.dto.DashboardStatsDto;
import com.sunasterisk.bookingtours.entity.BookingStatus;
import com.sunasterisk.bookingtours.repository.BookingRepository;
import com.sunasterisk.bookingtours.repository.TourRepository;
import com.sunasterisk.bookingtours.repository.UserRepository;
import com.sunasterisk.bookingtours.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * Triển khai {@link DashboardService}.
 *
 * <p>Tất cả truy vấn chỉ đọc nên được đánh dấu {@code readOnly = true}
 * để tối ưu hiệu năng và tránh lock không cần thiết.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final UserRepository userRepository;
    private final TourRepository tourRepository;
    private final BookingRepository bookingRepository;

    /**
     * {@inheritDoc}
     *
     * <p>Tính toán:
     * <ul>
     *   <li>totalUsers — {@code COUNT(*)} trên bảng users</li>
     *   <li>totalTours — {@code COUNT(*)} trên bảng tours</li>
     *   <li>bookingsToday — đếm booking có {@code created_at} trong ngày hôm nay</li>
     *   <li>revenueThisMonth — {@code SUM(total_price)} của booking CONFIRMED
     *       có {@code created_at} trong tháng hiện tại</li>
     * </ul>
     */
    @Override
    public DashboardStatsDto getStats() {
        long totalUsers = userRepository.count();
        long totalTours = tourRepository.count();

        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(23, 59, 59);
        long bookingsToday = bookingRepository.countBookingsBetween(startOfDay, endOfDay);

        YearMonth currentMonth = YearMonth.now();
        LocalDateTime startOfMonth = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime endOfMonth = currentMonth.atEndOfMonth().atTime(23, 59, 59);
        BigDecimal revenueThisMonth = bookingRepository.sumRevenueByStatusBetween(
                BookingStatus.CONFIRMED, startOfMonth, endOfMonth);

        return DashboardStatsDto.builder()
                .totalUsers(totalUsers)
                .totalTours(totalTours)
                .bookingsToday(bookingsToday)
                .revenueThisMonth(revenueThisMonth)
                .build();
    }
}
