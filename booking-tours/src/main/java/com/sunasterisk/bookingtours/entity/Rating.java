package com.sunasterisk.bookingtours.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "ratings",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_tour_id_user_id",
                columnNames = {"tour_id", "user_id"}
        )
)
@EntityListeners(AuditingEntityListener.class)
public class Rating {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tour được đánh giá. Xóa tour → xóa toàn bộ ratings liên quan (CASCADE).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tour_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ratings_tour"))
    private Tour tour;

    /**
     * Người dùng thực hiện đánh giá. Mỗi user chỉ được rating 1 lần / tour.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ratings_user"))
    private User user;

    /**
     * Điểm đánh giá từ 1 đến 5.
     *
     * @Check tương ứng với constraint CHECK (score BETWEEN 1 AND 5) trong schema.
     */
    @Column(name = "score", nullable = false)
    @Min(1)
    @Max(5)
    private Short score;

    /**
     * Thời điểm rating, tự động gán bởi Spring Auditing, không cho phép cập nhật lại.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
