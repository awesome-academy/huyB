package com.sunasterisk.bookingtours.entity;

/**
 * OAuth2 provider được hỗ trợ.
 * Giá trị phải khớp với registrationId trong Spring Security OAuth2
 * (chữ HOA để giữ nhất quán với cột provider VARCHAR(20) trong DB).
 */
public enum OAuthProvider {
    GOOGLE,
    FACEBOOK,
    TWITTER
}
