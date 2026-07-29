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
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Utility class quản lý JWT:
 * - Tạo token từ Authentication
 * - Validate token
 * - Đọc claims (username, role)
 */
@Component
public class JwtUtils {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    /**
     * -- GETTER --
     * Trả về tên cookie JWT — được dùng bởi
     * để tìm đúng cookie trong request.
     * <p>
     * Các field nhạy cảm (
     * ,
     * ,
     * )
     * không có getter công khai để tránh lộ thông tin ra ngoài class.
     */
    @Getter
    @Value("${app.jwt.cookie-name}")
    private String cookieName;

    /**
     * true ở prod (HTTPS), false ở dev (HTTP localhost)
     */
    @Value("${app.jwt.cookie-secure:false}")
    private boolean cookieSecure;

    // RFC 1123 format — dùng cho Expires attribute
    private static final DateTimeFormatter RFC1123 =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.ENGLISH);

    // ----------------------------------------------------------------
    // Cookie helpers — dùng Set-Cookie header để set SameSite
    // ----------------------------------------------------------------

    /**
     * Đặt JWT vào HttpOnly cookie với SameSite=Strict.
     * - Secure: bật khi cookie-secure=true (production HTTPS)
     * - Max-Age + Expires: đặt cả hai để tương thích mọi browser
     */
    public void addJwtCookie(HttpServletResponse response, String token) {
        long maxAgeSeconds = expirationMs / 1000;
        String expires = toHttpDate(System.currentTimeMillis() + expirationMs);
        response.addHeader("Set-Cookie", buildCookieHeader(token, maxAgeSeconds, expires));
    }

    /**
     * Xóa JWT cookie bằng cách set Max-Age=0 và Expires trong quá khứ.
     * Dùng đúng cùng attributes (kể cả Secure, SameSite) với addJwtCookie
     * để browser nhận diện đúng cookie cần xóa.
     */
    public void clearJwtCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildCookieHeader("", 0, "Thu, 01 Jan 1970 00:00:00 GMT"));
    }

    // ----------------------------------------------------------------
    // Private builder — nguồn sự thật duy nhất cho cookie attributes
    // ----------------------------------------------------------------

    private String buildCookieHeader(String value, long maxAgeSeconds, String expires) {
        StringBuilder sb = new StringBuilder();
        sb.append(cookieName).append("=").append(value)
                .append("; HttpOnly")
                .append("; Path=/")
                .append("; Max-Age=").append(maxAgeSeconds)
                .append("; Expires=").append(expires)
                // SameSite=Lax (not Strict) is required for OAuth2 redirect flows: when the browser
                // follows the post-OAuth2 redirect back to this app, the navigation chain originated
                // from the provider (facebook.com, etc.) and SameSite=Strict causes the JWT cookie
                // to be blocked on that first same-site hop. Lax still blocks cross-site AJAX/POST
                // requests; the CSRF token provides the additional defense layer.
                .append("; SameSite=Lax");
        if (cookieSecure) {
            sb.append("; Secure");
        }
        return sb.toString();
    }

    private String toHttpDate(long epochMillis) {
        return ZonedDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC
        ).format(RFC1123);
    }

    // ----------------------------------------------------------------
    // Token helpers
    // ----------------------------------------------------------------

    /**
     * Token chỉ mang danh tính (subject = email). KHÔNG nhúng role vào token:
     * role và trạng thái tài khoản được đọc lại từ DB ở mỗi request
     * (JwtAuthenticationFilter) để lock/đổi role có hiệu lực ngay lập tức.
     */
    public String generateToken(Authentication authentication) {
        return Jwts.builder()
                .subject(authentication.getName())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
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
