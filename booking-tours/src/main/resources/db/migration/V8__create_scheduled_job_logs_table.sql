CREATE TABLE scheduled_job_logs (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    job_name         VARCHAR(100) NOT NULL,
    status           ENUM('SUCCESS','FAILED','SKIPPED') NOT NULL,
    records_processed INT          DEFAULT 0,
    duration_ms      BIGINT,
    error_message    TEXT,
    executed_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_job_logs_job_name   (job_name),
    INDEX idx_job_logs_executed_at (executed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
