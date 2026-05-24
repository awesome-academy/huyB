package com.sunasterisk.bookingtours.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Utility class quản lý JWT:
 * - Tạo token từ Authentication
 * - Validate token
 * - Đọc claims (username, role)
 */
@Component
@Getter
public class JwtUtils {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    @Value("${app.jwt.cookie-name}")
    private String cookieName;

    // ----------------------------------------------------------------
    // Cookie helpers — dùng Set-Cookie header để set SameSite
    // ----------------------------------------------------------------

    /**
     * Đặt JWT vào HttpOnly cookie với SameSite=Strict.
     * Dùng response header thay vì Cookie API vì Servlet
     * không có setSameSite() method.
     */
    public void addJwtCookie(HttpServletResponse response, String token) {
        String cookieValue = cookieName + "=" + token
                + "; HttpOnly"
                + "; Path=/"
                + "; Max-Age=" + (expirationMs / 1000)
                + "; SameSite=Strict";
        // Thêm "; Secure" khi deploy HTTPS
        response.addHeader("Set-Cookie", cookieValue);
    }

    /**
     * Xóa JWT cookie bằng cách set Max-Age=0.
     */
    public void clearJwtCookie(HttpServletResponse response) {
        String cookieValue = cookieName + "="
                + "; HttpOnly"
                + "; Path=/"
                + "; Max-Age=0"
                + "; SameSite=Strict";
        response.addHeader("Set-Cookie", cookieValue);
    }

    // ----------------------------------------------------------------
    // Token helpers
    // ----------------------------------------------------------------

    public String generateToken(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("ROLE_USER");

        return Jwts.builder()
                .subject(authentication.getName())
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public String getRoleFromToken(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // ----------------------------------------------------------------

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
