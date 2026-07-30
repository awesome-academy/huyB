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
    private static final int STALE_HOURS = 48;

    private final BookingRepository bookingRepository;
    private final ScheduledJobLogRepository jobLogRepository;

    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void run() {
        long start = System.currentTimeMillis();
        LocalDateTime cutoff = LocalDateTime.now().minusHours(STALE_HOURS);
        log.info("[{}] Cancelling PENDING bookings created before {}", JOB_NAME, cutoff);

        try {
            List<Booking> stale = bookingRepository.findStalePendingBookings(
                    BookingStatus.PENDING, cutoff);

            for (Booking booking : stale) {
                booking.setStatus(BookingStatus.CANCELLED);
            }
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
