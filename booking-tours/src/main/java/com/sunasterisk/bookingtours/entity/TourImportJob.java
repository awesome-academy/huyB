package com.sunasterisk.bookingtours.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Theo dõi trạng thái và kết quả của mỗi lần import tour từ file Excel.
 * Không kế thừa {@link BaseEntity} vì bảng không có cột {@code updated_at}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tour_import_jobs")
public class TourImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", length = 255, nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ImportJobStatus status = ImportJobStatus.PENDING;

    @Column(name = "total_rows")
    @Builder.Default
    private Integer totalRows = 0;

    @Column(name = "success_rows")
    @Builder.Default
    private Integer successRows = 0;

    @Column(name = "failed_rows")
    @Builder.Default
    private Integer failedRows = 0;

    /** JSON array of {row, reason} objects for failed rows. */
    @Column(name = "error_details", columnDefinition = "MEDIUMTEXT")
    private String errorDetails;

    /** ID của admin đã tạo job (null nếu user bị xóa). */
    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public enum ImportJobStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}
