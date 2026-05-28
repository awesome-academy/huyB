package com.sunasterisk.bookingtours.service.impl;

import com.sunasterisk.bookingtours.entity.OAuthAccount;
import com.sunasterisk.bookingtours.entity.OAuthProvider;
import com.sunasterisk.bookingtours.entity.Role;
import com.sunasterisk.bookingtours.entity.User;
import com.sunasterisk.bookingtours.repository.OAuthAccountRepository;
import com.sunasterisk.bookingtours.repository.RoleRepository;
import com.sunasterisk.bookingtours.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xử lý việc tìm/tạo User và OAuthAccount khi đăng nhập qua Google OIDC.
 *
 * <p>Tách riêng khỏi {@link CustomOAuth2UserService} vì:</p>
 * <ul>
 *   <li>Spring AOP không intercept self-invocation → @Transactional sẽ bị bỏ qua
 *       nếu gọi method @Transactional từ chính class đó.</li>
 *   <li>HTTP calls tới Google (delegate.loadUser) KHÔNG nên nằm trong transaction
 *       → Tránh giữ DB connection trong khi chờ network response.</li>
 *   <li>Dùng {@code REQUIRES_NEW}: luôn tạo transaction mới, commit độc lập,
 *       không bị rollback bởi bất kỳ outer transaction nào từ Spring Security.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthUserRegistrationService {

    private final UserRepository userRepository;
    private final OAuthAccountRepository oAuthAccountRepository;
    private final RoleRepository roleRepository;

    /**
     * Tìm hoặc tạo {@link User} và {@link OAuthAccount} theo thông tin từ OIDC claims.
     * Chạy trong {@code REQUIRES_NEW} transaction để đảm bảo commit ngay lập tức,
     * độc lập với bất kỳ outer transaction nào.
     *
     * @return User entity đã được lưu và active
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public User findOrCreateUser(String email, String name, String avatarUrl,
                                 OAuthProvider provider, String providerUserId) {

        // ── 1. Tìm hoặc tạo User ──────────────────────────────────────────────
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    log.info("[OAuth] Creating new user: email={}", email);
                    return createUser(email, name, avatarUrl);
                });

        log.info("[OAuth] Found/created user: id={}, email={}", user.getId(), email);

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new IllegalStateException("User account is disabled: " + email);
        }

        // ── 2. Tìm hoặc tạo OAuthAccount ─────────────────────────────────────
        boolean oauthExists = oAuthAccountRepository
                .findByProviderAndProviderUserId(provider, providerUserId)
                .isPresent();

        if (!oauthExists) {
            log.info("[OAuth] Linking new oauth_account: provider={}, sub={}, userId={}",
                    provider, providerUserId, user.getId());
            try {
                OAuthAccount oAuthAccount = OAuthAccount.builder()
                        .user(user)
                        .provider(provider)
                        .providerUserId(providerUserId)
                        .build();
                // saveAndFlush → INSERT ngay lập tức trong transaction này,
                // không chờ đến lúc flush tự động
                oAuthAccountRepository.saveAndFlush(oAuthAccount);
                log.info("[OAuth] oauth_account saved: id={}", oAuthAccount.getId());
            } catch (DataIntegrityViolationException e) {
                // Race condition: một request khác đã tạo record này vừa xong
                log.warn("[OAuth] oauth_account already exists (race condition), skipping: provider={}, sub={}",
                        provider, providerUserId);
            }
        } else {
            log.debug("[OAuth] oauth_account already exists: provider={}, sub={}", provider, providerUserId);
        }

        // ── 3. Khởi tạo lazy proxy User.role trong khi session còn mở ─────────
        // CustomOAuth2UserService gọi user.getRole().getName() SAU khi transaction này đóng
        // → REQUIRES_NEW commit → session đóng → LazyInitializationException nếu chưa init.
        // Hibernate.initialize() buộc SELECT role ngay bây giờ, trong transaction,
        // để proxy không cần session nữa khi được truy cập từ bên ngoài.
        Hibernate.initialize(user.getRole());

        return user;
    }

    // ────────────────────────────────────────────────────────────────────────

    private User createUser(String email, String name, String avatarUrl) {
        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new IllegalStateException(
                        "Role USER not found. Please seed the roles table."));

        User newUser = User.builder()
                .email(email)
                .fullName(name != null && !name.isBlank() ? name : email)
                .avatarUrl(avatarUrl)
                .role(userRole)
                .isActive(true)
                // password = null — OIDC user không đăng nhập bằng form login
                .build();

        User saved = userRepository.saveAndFlush(newUser);
        log.info("[OAuth] New user created: id={}, email={}", saved.getId(), saved.getEmail());
        return saved;
    }
}
