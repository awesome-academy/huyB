package com.sunasterisk.bookingtours.config;

import com.sunasterisk.bookingtours.repository.UserRepository;
import com.sunasterisk.bookingtours.service.impl.CustomOAuth2UserService;
import com.sunasterisk.bookingtours.service.impl.CustomStandardOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final CustomAuthenticationSuccessHandler oAuth2SuccessHandler;
    private final JwtUtils jwtUtils;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomStandardOAuth2UserService customStandardOAuth2UserService;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final UserRepository userRepository;

    /**
     * JwtAuthenticationFilter không phải @Component để tránh bị Spring Boot
     * tự đăng ký vào Servlet filter chain (sẽ chạy 2 lần).
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        // RequestAttributeSecurityContextRepository: context sống trong phạm vi request,
        // không persist vào session. SessionManagementFilter.containsContext() trả về true
        // sau khi JwtAuthenticationFilter gọi saveContext() → ngăn CsrfAuthenticationStrategy
        // rotate CSRF token trên mỗi JWT-authenticated request.
        return new RequestAttributeSecurityContextRepository();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(SecurityContextRepository securityContextRepository) {
        return new JwtAuthenticationFilter(jwtUtils, userRepository, securityContextRepository);
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return new ProviderManager(provider);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    SecurityContextRepository securityContextRepository) throws Exception {
        http
                // Bật CSRF với CookieCsrfTokenRepository (token lưu trong XSRF-TOKEN cookie).
                // XorCsrfTokenRequestAttributeHandler (default từ Spring Security 6+):
                //   - XOR-encode token cho mỗi form request → BREACH protection
                //   - Đọc cookie trực tiếp khi validate, không phụ thuộc deferred subscription
                //   - Thymeleaf th:action tự inject _csrf hidden field với giá trị XOR-encoded
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler())
                )

                // Tắt HTTP Basic Authentication mặc định
                .httpBasic(AbstractHttpConfigurer::disable)

                // Session: IF_REQUIRED thay vì STATELESS vì OAuth2 Authorization Code flow
                // cần session để lưu "state" parameter giữa bước redirect đến Google và callback.
                // Sau khi OAuth2 hoàn thành, JWT cookie được set → mọi request tiếp theo
                // đều xác thực qua JWT (không cần session nữa).
                .sessionManagement(sm -> sm
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

                // RequestAttributeSecurityContextRepository: context sống trong phạm vi request,
                // không persist sang session (không khôi phục Authentication từ session ở request sau).
                // JwtAuthenticationFilter gọi saveContext() → SessionManagementFilter thấy
                // containsContext()=true → CsrfAuthenticationStrategy KHÔNG rotate CSRF token.
                // Điều này ngăn form bị stale CSRF sau mỗi request GET authenticated.
                // OAuth2 state param dùng repository riêng (HttpSessionOAuth2AuthorizationRequestRepository),
                // không bị ảnh hưởng.
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))

                // Phân quyền theo URL
                .authorizeHttpRequests(auth -> auth
                        // Rating tour là hành động thay đổi dữ liệu → bắt buộc đăng nhập.
                        // Phải đặt TRƯỚC "/tours/**".permitAll() vì matcher đầu tiên khớp sẽ thắng;
                        // nếu để sau, POST /tours/{id}/rate sẽ lọt permitAll và tới controller với
                        // authentication == null → NPE.
                        .requestMatchers(HttpMethod.POST, "/tours/*/rate").authenticated()

                        // Cho phép tất cả truy cập vào endpoints công khai
                        .requestMatchers(
                                "/",
                                "/tours/**",
                                "/auth/**",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/uploads/**",
                                "/error/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // Reviews: chỉ trang danh sách và trang chi tiết là công khai.
                        // Mọi endpoint còn lại (/new, /edit, POST comment/like/delete...)
                        // rơi xuống anyRequest().authenticated() bên dưới.
                        .requestMatchers(HttpMethod.GET, "/reviews", "/reviews/{id:\\d+}").permitAll()

                        // Chỉ cho phép ROLE_ADMIN truy cập /admin/**
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // Yêu cầu xác thực cho tất cả request còn lại
                        .anyRequest().authenticated()
                )

                // JWT filter chạy trước UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthenticationFilter(securityContextRepository),
                        UsernamePasswordAuthenticationFilter.class)

                // Logout: chỉ chấp nhận POST (CSRF token bắt buộc) → xóa JWT cookie → redirect
                .logout(logout -> logout
                        .logoutUrl("/auth/logout")           // chỉ POST mới được xử lý
                        .addLogoutHandler((request, response, auth) ->
                                jwtUtils.clearJwtCookie(response))
                        .logoutSuccessUrl("/auth/login?logout=true")
                        .permitAll()
                )

                // OAuth2 Google — custom userService tìm/tạo user → success handler tạo JWT + redirect theo role
                // Dùng .oidcUserService() vì Google dùng OIDC (scope=openid,profile,email),
                // Spring Security sẽ gọi OidcUserService — KHÔNG phải DefaultOAuth2UserService.
                // .userService() chỉ dành cho standard OAuth2 (không có openid scope).
                // authorizationEndpoint: thêm prompt=select_account để Google luôn hiện màn hình
                // chọn tài khoản, tránh tự dùng lại session cũ khi user muốn đổi tài khoản.
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/auth/login")
                        .authorizationEndpoint(ep -> ep
                                .authorizationRequestResolver(
                                        new CustomAuthorizationRequestResolver(clientRegistrationRepository))
                        )
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(customOAuth2UserService)          // Google (OIDC)
                                .userService(customStandardOAuth2UserService)      // Facebook, Twitter (standard OAuth2)
                        )
                        .successHandler(oAuth2SuccessHandler)
                )

                // Security headers bổ sung ngoài defaults (nosniff, X-Frame-Options...)
                // CSP: script/style chỉ từ chính app và cdn.jsdelivr.net (Bootstrap).
                // 'unsafe-inline' là bắt buộc vì template còn dùng inline <script>
                // và th:onclick — vẫn chặn được script từ mọi origin khác.
                // HSTS chỉ được browser áp dụng trên HTTPS nên an toàn với dev HTTP.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; "
                                        + "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; "
                                        + "font-src 'self' https://cdn.jsdelivr.net; "
                                        + "img-src 'self' data: https:; "
                                        + "form-action 'self'; "
                                        + "frame-ancestors 'none'; "
                                        + "base-uri 'self'"))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                )

                // Unauthenticated → redirect login; Access denied → 403
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                response.sendRedirect(request.getContextPath() + "/auth/login"))
                        .accessDeniedPage("/error/403")
                );

        return http.build();
    }
}
