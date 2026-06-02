package com.sunasterisk.bookingtours.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entity đại diện cho một tour du lịch trong hệ thống.
 * Kế thừa {@link BaseEntity} để tự động quản lý created_at / updated_at.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tours")
public class Tour extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tiêu đề tour, bắt buộc, tối đa 255 ký tự.
     */
    @Column(name = "title", length = 255, nullable = false)
    private String title;

    /**
     * Mô tả chi tiết tour (TEXT không giới hạn).
     */
    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    /**
     * Giá tour (VND), tối đa 12 chữ số, 2 chữ số thập phân.
     */
    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /**
     * Số ngày của tour.
     */
    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    /**
     * Số người tham gia tối đa.
     */
    @Column(name = "max_participants", nullable = false)
    private Integer maxParticipants;

    /**
     * Địa điểm khởi hành.
     */
    @Column(name = "departure_location", length = 255, nullable = false)
    private String departureLocation;

    /**
     * Điểm đến của tour.
     */
    @Column(name = "destination", length = 255, nullable = false)
    private String destination;

    /**
     * Ngày khởi hành, có thể null nếu chưa xác định.
     */
    @Column(name = "departure_date")
    private LocalDate departureDate;

    /**
     * URL ảnh thumbnail (lưu đường dẫn tương đối, ví dụ /uploads/xxx.jpg).
     */
    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    /**
     * Danh mục của tour.
     * LAZY fetch để tránh N+1 khi chỉ cần thông tin tour.
     * SET NULL trong DB nên có thể null nếu category bị xóa.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", foreignKey = @ForeignKey(name = "fk_tours_category"))
    private Category category;

    /**
     * Trạng thái tour: {@link TourStatus#ACTIVE} (đang hoạt động) hoặc
     * {@link TourStatus#INACTIVE} (tạm ngừng).
     * Lưu dưới dạng chuỗi trong DB (ACTIVE / INACTIVE).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private TourStatus status = TourStatus.ACTIVE;

    /**
     * Điểm đánh giá trung bình của tour (1.0 – 5.0), tính từ bảng ratings.
     * Mặc định 0 khi chưa có rating nào.
     */
    @Column(name = "avg_rating", precision = 2, scale = 1, nullable = false)
    @Builder.Default
    private BigDecimal avgRating = BigDecimal.ZERO;
}
