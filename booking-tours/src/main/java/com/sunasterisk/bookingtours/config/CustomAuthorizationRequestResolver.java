package com.sunasterisk.bookingtours.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Thêm tham số {@code prompt=select_account} vào authorization request gửi lên Google.
 * Chỉ áp dụng cho Google — Facebook và Twitter không hỗ trợ tham số này.
 */
public class CustomAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private static final String REGISTRATION_BASE_URI = "/oauth2/authorization";
    private static final String GOOGLE = "google";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public CustomAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, REGISTRATION_BASE_URI);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest resolved = delegate.resolve(request);
        if (resolved == null) return null;
        String registrationId = extractRegistrationId(request);
        return GOOGLE.equals(registrationId) ? addPromptSelectAccount(resolved) : resolved;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest resolved = delegate.resolve(request, clientRegistrationId);
        if (resolved == null) return null;
        return GOOGLE.equals(clientRegistrationId) ? addPromptSelectAccount(resolved) : resolved;
    }

    private OAuth2AuthorizationRequest addPromptSelectAccount(OAuth2AuthorizationRequest request) {
        return OAuth2AuthorizationRequest.from(request)
                .additionalParameters(params -> params.put("prompt", "select_account"))
                .build();
    }

    /** Trích registrationId từ URI dạng /oauth2/authorization/{registrationId} */
    private String extractRegistrationId(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int idx = uri.lastIndexOf('/');
        return idx >= 0 ? uri.substring(idx + 1) : null;
    }
}
