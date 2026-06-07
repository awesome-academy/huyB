package com.sunasterisk.bookingtours.entity;

import jakarta.persistence.*;
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
        name = "likes",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_review_id_user_id",
                columnNames = {"review_id", "user_id"}
        )
)
@EntityListeners(AuditingEntityListener.class)
public class Like {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Review được like. Xóa review → xóa toàn bộ likes liên quan (CASCADE).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "review_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_likes_review")
    )
    private Review review;

    /**
     * Người dùng đã thực hiện like. Xóa user → xóa toàn bộ likes của user (CASCADE).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_likes_user")
    )
    private User user;

    /**
     * Thời điểm like, tự động gán bởi Spring Auditing, không cho phép cập nhật lại.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
