package com.sunasterisk.bookingtours.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Dùng cho OAuth2 login:
 * 1. Generate JWT từ Authentication
 * 2. Set JWT vào HttpOnly cookie (SameSite=Strict)
 * 3. Redirect theo role (ADMIN → /admin, USER → /)
 */
@Component
@RequiredArgsConstructor
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomAuthenticationSuccessHandler.class);

    private final JwtUtils jwtUtils;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        @NonNull HttpServletResponse response,
                                        @NonNull Authentication authentication) throws IOException {
        // 1. Tạo JWT và đặt vào HttpOnly cookie (SameSite=Strict)
        String token = jwtUtils.generateToken(authentication);
        // DEBUG only — token là credential, không được log ở INFO/prod
        log.debug("JWT_TOKEN generated for [{}]: {}", authentication.getName(), token);
        jwtUtils.addJwtCookie(response, token);

        // 2. Redirect theo role
        String redirectUrl = determineRedirectUrl(authentication);
        response.sendRedirect(request.getContextPath() + redirectUrl);
    }

    private String determineRedirectUrl(Authentication authentication) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                return "/admin";
            }
        }
        return "/";
    }
}
