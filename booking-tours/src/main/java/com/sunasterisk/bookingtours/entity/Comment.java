package com.sunasterisk.bookingtours.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "comments")
@EntityListeners(AuditingEntityListener.class)
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Review mà comment này thuộc về. Xóa review → xóa toàn bộ comment (CASCADE).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false, foreignKey = @ForeignKey(name = "fk_comments_review"))
    private Review review;

    /**
     * Người dùng đã viết comment.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_comments_user"))
    private User user;

    /**
     * Self-referencing để hỗ trợ reply 1 cấp.
     * NULL  → comment gốc.
     * NOT NULL → reply của một comment gốc.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    /**
     * Danh sách reply trực tiếp của comment này (chỉ sâu 1 cấp).
     * CascadeType.ALL nghĩa là mọi thao tác JPA thực hiện trên entity cha sẽ tự động lan truyền xuống entity con.
     * orphanRemoval = true — khi một entity con bị tách khỏi collection của cha (trở thành "mồ côi"), JPA sẽ tự động xóa nó khỏi DB.
     */
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> replies = new ArrayList<>();

    /**
     * Nội dung comment, không được để trống.
     */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /**
     * Soft delete — không xóa bản ghi khỏi DB.
     * TRUE  → comment đã bị ẩn/xóa mềm.
     * FALSE → comment đang hiển thị bình thường (mặc định).
     */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    /**
     * Thời điểm tạo, tự động gán bởi Spring Auditing, không cho phép cập nhật lại.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
