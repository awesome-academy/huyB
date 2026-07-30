package com.sunasterisk.bookingtours.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Lưu lịch sử chạy của các scheduled job: tên job, trạng thái, số bản ghi xử lý, thời gian.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "scheduled_job_logs")
public class ScheduledJobLog {

    public enum JobStatus { SUCCESS, FAILED, SKIPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false, length = 100)
    private String jobName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private JobStatus status;

    @Column(name = "records_processed")
    @Builder.Default
    private Integer recordsProcessed = 0;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Builder.Default
    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt = LocalDateTime.now();
}
