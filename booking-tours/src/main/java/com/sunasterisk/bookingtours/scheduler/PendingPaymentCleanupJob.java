package com.sunasterisk.bookingtours.scheduler;

import com.sunasterisk.bookingtours.entity.Booking;
import com.sunasterisk.bookingtours.entity.BookingStatus;
import com.sunasterisk.bookingtours.entity.ScheduledJobLog;
import com.sunasterisk.bookingtours.entity.ScheduledJobLog.JobStatus;
import com.sunasterisk.bookingtours.repository.BookingRepository;
import com.sunasterisk.bookingtours.repository.ScheduledJobLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Job chạy hàng ngày lúc 01:00 để huỷ các booking PENDING quá 48h chưa có payment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingPaymentCleanupJob {

    private static final String JOB_NAME = "PendingPaymentCleanupJob";
    // 48h: đủ thời gian cho user hoàn tất thanh toán qua các cổng chậm (chuyển khoản, ví)
    private static final int STALE_HOURS = 48;

    private final BookingRepository bookingRepository;
    private final ScheduledJobLogRepository jobLogRepository;

    // Chạy lúc 01:00 — sau AutoCompleteBookingJob (00:30) để tránh tranh chấp transaction
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void run() {
        long start = System.currentTimeMillis();
        // cutoff = thời điểm hiện tại - 48h; booking tạo trước mốc này mà chưa có payment → stale
        LocalDateTime cutoff = LocalDateTime.now().minusHours(STALE_HOURS);
        log.info("[{}] Cancelling PENDING bookings created before {}", JOB_NAME, cutoff);

        try {
            // findStalePendingBookings kiểm tra: status = PENDING, createdAt < cutoff, chưa có payment
            List<Booking> stale = bookingRepository.findStalePendingBookings(
                    BookingStatus.PENDING, cutoff);

            for (Booking booking : stale) {
                booking.setStatus(BookingStatus.CANCELLED);
            }
            // Toàn bộ batch trong một transaction — rollback nếu có lỗi giữa chừng
            bookingRepository.saveAll(stale);

            long duration = System.currentTimeMillis() - start;
            log.info("[{}] Cancelled {} stale bookings in {}ms", JOB_NAME, stale.size(), duration);

            jobLogRepository.save(ScheduledJobLog.builder()
                    .jobName(JOB_NAME)
                    .status(JobStatus.SUCCESS)
                    .recordsProcessed(stale.size())
                    .durationMs(duration)
                    .executedAt(LocalDateTime.now())
                    .build());

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[{}] Failed after {}ms: {}", JOB_NAME, duration, e.getMessage(), e);
            // Ghi log lỗi vào DB để admin phát hiện nếu job bị skip do exception
            jobLogRepository.save(ScheduledJobLog.builder()
                    .jobName(JOB_NAME)
                    .status(JobStatus.FAILED)
                    .durationMs(duration)
                    .errorMessage(e.getMessage())
                    .executedAt(LocalDateTime.now())
                    .build());
        }
    }
}
