package com.sunasterisk.bookingtours.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Mapping tới bảng oauth_accounts.
 * Bảng này chỉ có created_at (không có updated_at) nên
 * không extend BaseEntity — khai báo trực tiếp @CreatedDate.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "user")
@Entity
@Table(
        name = "oauth_accounts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_oauth_provider_user_id",
                columnNames = {"provider", "provider_user_id"}
        )
)
@EntityListeners(AuditingEntityListener.class)
public class OAuthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * User sở hữu OAuth account này.
     * ON DELETE CASCADE được xử lý ở DB, JPA dùng LAZY để tránh N+1.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_oauth_accounts_user")
    )
    private User user;

    /**
     * Nhà cung cấp OAuth2 (GOOGLE, FACEBOOK, TWITTER).
     * Lưu dưới dạng String trong DB (EnumType.STRING).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 20, nullable = false)
    private OAuthProvider provider;

    /**
     * ID của user bên phía provider (Google: trường "sub").
     */
    @Column(name = "provider_user_id", length = 255, nullable = false)
    private String providerUserId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
