-- T4.4: Bảng lưu trạng thái và kết quả của từng lần import tour từ Excel
CREATE TABLE tour_import_jobs (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    file_name     VARCHAR(255) NOT NULL,
    status        ENUM('PENDING','PROCESSING','COMPLETED','FAILED') NOT NULL DEFAULT 'PENDING',
    total_rows    INT DEFAULT 0,
    success_rows  INT DEFAULT 0,
    failed_rows   INT DEFAULT 0,
    error_details MEDIUMTEXT,
    created_by    BIGINT,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at  DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_tour_import_jobs_status (status),
    INDEX idx_tour_import_jobs_created_by (created_by),
    CONSTRAINT fk_import_jobs_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
