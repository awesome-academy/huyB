package com.sunasterisk.bookingtours.config;

import com.sunasterisk.bookingtours.entity.User;
import com.sunasterisk.bookingtours.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Chạy một lần mỗi request:
 * 1. Đọc JWT từ HttpOnly cookie
 * 2. Validate token (chữ ký + hạn)
 * 3. Load user từ DB theo subject — kiểm tra tài khoản còn active và lấy role
 *    HIỆN TẠI (không tin role claim trong token). Nhờ đó lock/unlock và đổi role
 *    có hiệu lực ngay lập tức thay vì phải chờ token hết hạn.
 * 4. Set Authentication vào SecurityContext và persist vào repository
 *    (RequestAttributeSecurityContextRepository) để SessionManagementFilter
 *    nhận biết context đã có — tránh CsrfAuthenticationStrategy rotate token
 *    trên mỗi request JWT-authenticated.
 * <p>
 * KHÔNG đăng ký là @Component để tránh bị Spring Boot
 * tự động thêm vào Servlet filter chain (sẽ bị chạy 2 lần).
 * Bean được tạo trong SecurityConfig.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /**
     * Static resources không cần Authentication — bỏ qua để tránh
     * query DB vô ích cho mỗi file css/js/ảnh.
     */
    private static final List<String> STATIC_PREFIXES =
            List.of("/css/", "/js/", "/images/", "/uploads/", "/favicon");

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final SecurityContextRepository securityContextRepository;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return STATIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = extractTokenFromCookie(request);
        // DEBUG only — token là credential, không được log ở INFO/prod
        if (token != null) {
            log.debug("JWT_TOKEN extracted from cookie [{}]: {}", request.getRequestURI(), token);
        }

        // Guard: chỉ xử lý khi SecurityContext chưa có Authentication
        if (token != null
                && jwtUtils.validateToken(token)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String username = jwtUtils.getUsernameFromToken(token);

            // Token chỉ chứng minh danh tính — trạng thái tài khoản và role
            // luôn được đọc lại từ DB tại thời điểm request.
            User user = userRepository.findByEmailWithRole(username).orElse(null);

            if (user != null && Boolean.TRUE.equals(user.getIsActive())) {
                String role = (user.getRole() != null)
                        ? "ROLE_" + user.getRole().getName()
                        : "ROLE_USER";

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                List.of(new SimpleGrantedAuthority(role)));

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContext context = SecurityContextHolder.getContext();
                context.setAuthentication(authentication);
                // Persist context vào RequestAttributeSecurityContextRepository —
                // SessionManagementFilter.containsContext() sẽ trả về true
                // → CsrfAuthenticationStrategy không rotate CSRF token.
                securityContextRepository.saveContext(context, request, response);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (jwtUtils.getCookieName().equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
