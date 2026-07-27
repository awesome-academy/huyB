package com.sunasterisk.bookingtours.service.impl;

import com.sunasterisk.bookingtours.entity.OAuthProvider;
import com.sunasterisk.bookingtours.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Custom OIDC UserService — xử lý đăng nhập Google (scope=openid,profile,email).
 *
 * <p><b>Tại sao dùng OidcUserRequest thay vì OAuth2UserRequest?</b><br>
 * Khi scope chứa "openid", Spring Security kích hoạt OIDC flow và gọi
 * {@link OidcUserService} (KHÔNG phải DefaultOAuth2UserService).
 * Nếu dùng {@code OAuth2UserService<OAuth2UserRequest, OAuth2User>} và cấu hình
 * qua {@code .userService()}, service này sẽ KHÔNG BAO GIỜ được gọi với Google.</p>
 *
 * <p><b>Tại sao KHÔNG có @Transactional ở đây?</b><br>
 * {@code delegate.loadUser()} thực hiện HTTP call tới Google UserInfo endpoint.
 * Giữ DB connection trong suốt network call là anti-pattern (connection pool exhaustion).
 * Tất cả DB operations được delegate sang {@link OAuthUserRegistrationService}
 * với {@code REQUIRES_NEW} transaction riêng biệt.</p>
 *
 * <ol>
 *   <li>Delegate tới {@link OidcUserService} để xác thực OIDC ID Token (HTTP, ngoài transaction).</li>
 *   <li>Gọi {@link OAuthUserRegistrationService#findOrCreateUser} để tìm/tạo User + OAuthAccount.</li>
 *   <li>Trả về {@link DefaultOidcUser} với authority từ DB,
 *       nameAttributeKey="email" để JWT subject = email.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OAuthUserRegistrationService oAuthUserRegistrationService;

    // Delegate xử lý OIDC: validate ID Token, gọi UserInfo endpoint (HTTP)
    private final OidcUserService delegate = new OidcUserService();

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {

        // ── 1. Lấy OIDC user từ Google (HTTP call — NGOÀI transaction) ────────
        log.debug("[OAuth] Calling Google OIDC UserInfo endpoint...");
        OidcUser oidcUser = delegate.loadUser(userRequest);

        // ── 2. Xác định provider ─────────────────────────────────────────────
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuthProvider provider;
        try {
            provider = OAuthProvider.valueOf(registrationId.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("[OAuth] Unsupported OAuth2 provider: {}", registrationId);
            throw new OAuth2AuthenticationException("Unsupported OAuth2 provider: " + registrationId);
        }

        // ── 3. Trích xuất thông tin từ OIDC claims ───────────────────────────
        String providerUserId = oidcUser.getSubject();   // "sub" claim — unique ID phía Google
        String email          = oidcUser.getEmail();
        String name           = oidcUser.getFullName();
        String avatarUrl      = oidcUser.getPicture();

        log.info("[OAuth] OIDC callback: provider={}, email={}, sub={}",
                provider, email, providerUserId);

        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(
                    "Email not provided by OIDC provider: " + registrationId);
        }

        // ── 4. Tìm/tạo User + OAuthAccount trong REQUIRES_NEW transaction ─────
        // Tách riêng vào OAuthUserRegistrationService để:
        //   a) Không self-invocation (Spring AOP proxy hoạt động đúng)
        //   b) REQUIRES_NEW: commit độc lập, không bị rollback bởi outer context
        User user;
        try {
            user = oAuthUserRegistrationService.findOrCreateUser(
                    email, name, avatarUrl, provider, providerUserId);
        } catch (Exception e) {
            log.error("[OAuth] Failed to find/create user for email={}: {}", email, e.getMessage(), e);
            throw new OAuth2AuthenticationException(
                    "Failed to process OAuth2 user: " + e.getMessage());
        }

        // ── 5. Build authority theo role trong DB ─────────────────────────────
        String roleName = (user.getRole() != null)
                ? "ROLE_" + user.getRole().getName()
                : "ROLE_USER";
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(roleName);

        log.info("[OAuth] Login successful: email={}, role={}", email, roleName);

        // nameAttributeKey="email" → authentication.getName()=email → khớp JWT subject
        return new DefaultOidcUser(
                List.of(authority),
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                "email"
        );
    }
}
