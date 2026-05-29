package com.sunasterisk.bookingtours.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Thêm tham số {@code prompt=select_account} vào mỗi OAuth2 authorization request gửi lên Google.
 * <p>
 * Mục đích: buộc Google luôn hiện màn hình chọn tài khoản, tránh trường hợp Google
 * tự dùng lại session cũ khi user muốn đăng nhập bằng tài khoản khác.
 */
public class CustomAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private static final String REGISTRATION_BASE_URI = "/oauth2/authorization";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public CustomAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, REGISTRATION_BASE_URI);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return addPromptSelectAccount(delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return addPromptSelectAccount(delegate.resolve(request, clientRegistrationId));
    }

    /**
     * Thêm {@code prompt=select_account} vào additionalParameters của request.
     * Nếu request null (không khớp URL) thì trả về null nguyên bản.
     */
    private OAuth2AuthorizationRequest addPromptSelectAccount(OAuth2AuthorizationRequest request) {
        if (request == null) {
            return null;
        }
        return OAuth2AuthorizationRequest.from(request)
                .additionalParameters(params -> params.put("prompt", "select_account"))
                .build();
    }
}
