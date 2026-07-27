package com.sunasterisk.bookingtours.service.impl;

import com.sunasterisk.bookingtours.entity.OAuthProvider;
import com.sunasterisk.bookingtours.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Standard OAuth2 UserService cho Facebook và Twitter (không dùng OIDC).
 *
 * <p>Google dùng OIDC ({@link CustomOAuth2UserService}). Facebook và Twitter
 * dùng standard OAuth2 nên cần UserService riêng này.</p>
 *
 * <p>Twitter API v2 không trả về email — email sinh tự động dạng
 * {@code twitter_{id}@noemail.local} để đảm bảo NOT NULL constraint.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomStandardOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final OAuthUserRegistrationService oAuthUserRegistrationService;

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuthProvider provider;
        try {
            provider = OAuthProvider.valueOf(registrationId.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("[OAuth] Unsupported standard OAuth2 provider: {}", registrationId);
            throw new OAuth2AuthenticationException("Unsupported OAuth2 provider: " + registrationId);
        }

        String providerUserId;
        String email;
        String name;
        String avatarUrl = null;

        if (provider == OAuthProvider.FACEBOOK) {
            providerUserId = String.valueOf(oAuth2User.getAttribute("id"));
            email = oAuth2User.getAttribute("email");
            name = oAuth2User.getAttribute("name");
            if (email == null || email.isBlank()) {
                // Facebook email có thể thiếu nếu user không cấp quyền hoặc dùng phone number
                email = "facebook_" + providerUserId + "@noemail.local";
            }
        } else if (provider == OAuthProvider.TWITTER) {
            // Twitter API v2 trả về user data lồng trong object "data"
            Map<String, Object> data = oAuth2User.getAttribute("data");
            if (data == null) {
                throw new OAuth2AuthenticationException("Twitter user-info response missing 'data' field");
            }
            providerUserId = String.valueOf(data.get("id"));
            name = (String) data.get("name");
            // Twitter không trả về email — dùng username làm fallback identifier
            String username = (String) data.get("username");
            email = "twitter_" + (username != null ? username : providerUserId) + "@noemail.local";
        } else {
            throw new OAuth2AuthenticationException("Provider " + registrationId + " is not handled by this service");
        }

        log.info("[OAuth] Standard OAuth2 callback: provider={}, providerUserId={}", provider, providerUserId);

        User user;
        try {
            user = oAuthUserRegistrationService.findOrCreateUser(email, name, avatarUrl, provider, providerUserId);
        } catch (Exception e) {
            log.error("[OAuth] Failed to find/create user: provider={}, error={}", provider, e.getMessage(), e);
            throw new OAuth2AuthenticationException("Failed to process OAuth2 user");
        }

        String roleName = (user.getRole() != null)
                ? "ROLE_" + user.getRole().getName()
                : "ROLE_USER";

        log.info("[OAuth] Login successful: provider={}, userId={}, role={}", provider, user.getId(), roleName);

        // For Twitter, attributes["data"] is a nested Map — authentication.getName() would return its
        // toString(), breaking JWT subject lookup. Inject a flat "email" attribute and use it as the key
        // so getName() returns the actual email, consistent with Facebook ("id") and Google ("email").
        Map<String, Object> attrs = new HashMap<>(oAuth2User.getAttributes());
        String nameAttributeKey;
        if (provider == OAuthProvider.TWITTER) {
            attrs.put("synthetic_email", email);
            nameAttributeKey = "synthetic_email";
        } else {
            nameAttributeKey = "id";
        }

        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority(roleName)),
                attrs,
                nameAttributeKey
        );
    }
}
