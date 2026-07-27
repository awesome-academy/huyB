package com.sunasterisk.bookingtours.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity đại diện cho một bài review (đánh giá / bài viết) trong hệ thống.
 * Kế thừa {@link BaseEntity} để tự động quản lý created_at / updated_at.
 *
 * <p>Mỗi bài review thuộc về một {@link User}, có phân loại theo {@link ReviewType}
 * và trạng thái hiển thị theo {@link ReviewStatus}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "reviews")
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Người dùng đã viết bài review.
     * LAZY fetch để tránh N+1 khi chỉ cần thông tin review.
     * SET NULL trong DB nên có thể null nếu user bị xóa.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_reviews_user"))
    private User user;

    /**
     * Phân loại nội dung bài review: {@link ReviewType#PLACE}, {@link ReviewType#FOOD}
     * hoặc {@link ReviewType#NEWS}.
     * Lưu dưới dạng chuỗi trong DB.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    private ReviewType type;

    /**
     * Tiêu đề bài review, bắt buộc, tối đa 255 ký tự.
     */
    @Column(name = "title", length = 255, nullable = false)
    private String title;

    /**
     * Nội dung chi tiết bài review (TEXT không giới hạn).
     */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * Trạng thái hiển thị: {@link ReviewStatus#PUBLISHED} (công khai) hoặc
     * {@link ReviewStatus#HIDDEN} (bị ẩn).
     * Mặc định {@link ReviewStatus#PUBLISHED} khi tạo mới.
     * Lưu dưới dạng chuỗi trong DB.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.PUBLISHED;

    /**
     * Số lượt thích (like) của bài review.
     * Mặc định 0 khi chưa có lượt thích nào.
     */
    @Column(name = "likes_count")
    @Builder.Default
    private Integer likesCount = 0;
}