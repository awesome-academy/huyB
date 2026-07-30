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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Job chạy hàng ngày lúc 00:30 để chuyển booking CONFIRMED sang COMPLETED
 * khi ngày khởi hành của tour đã qua.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoCompleteBookingJob {

    private static final String JOB_NAME = "AutoCompleteBookingJob";

    private final BookingRepository bookingRepository;
    private final ScheduledJobLogRepository jobLogRepository;

    @Scheduled(cron = "0 30 0 * * *")
    @Transactional
    public void run() {
        long start = System.currentTimeMillis();
        log.info("[{}] Starting — checking CONFIRMED bookings past departure date", JOB_NAME);

        try {
            List<Booking> bookings = bookingRepository.findConfirmedBookingsPastDeparture(
                    BookingStatus.CONFIRMED, LocalDate.now());

            for (Booking booking : bookings) {
                booking.setStatus(BookingStatus.COMPLETED);
            }
            bookingRepository.saveAll(bookings);

            long duration = System.currentTimeMillis() - start;
            log.info("[{}] Completed {} bookings in {}ms", JOB_NAME, bookings.size(), duration);

            jobLogRepository.save(ScheduledJobLog.builder()
                    .jobName(JOB_NAME)
                    .status(JobStatus.SUCCESS)
                    .recordsProcessed(bookings.size())
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
