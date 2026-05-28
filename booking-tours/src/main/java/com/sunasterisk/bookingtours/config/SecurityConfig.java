package com.sunasterisk.bookingtours.config;

import com.sunasterisk.bookingtours.service.impl.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final CustomAuthenticationSuccessHandler oAuth2SuccessHandler;
    private final JwtUtils jwtUtils;
    private final CustomOAuth2UserService customOAuth2UserService;

    /**
     * JwtAuthenticationFilter không phải @Component để tránh bị Spring Boot
     * tự đăng ký vào Servlet filter chain (sẽ chạy 2 lần).
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtUtils);
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Bật CSRF với CookieCsrfTokenRepository (token lưu trong XSRF-TOKEN cookie).
                // Thymeleaf th:action tự inject hidden _csrf field → form submit hợp lệ.
                // Kết hợp HttpOnly + SameSite=Strict + CSRF token = defense-in-depth 3 lớp.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                )

                // Tắt HTTP Basic Authentication mặc định
                .httpBasic(AbstractHttpConfigurer::disable)

                // Session: IF_REQUIRED thay vì STATELESS vì OAuth2 Authorization Code flow
                // cần session để lưu "state" parameter giữa bước redirect đến Google và callback.
                // Sau khi OAuth2 hoàn thành, JWT cookie được set → mọi request tiếp theo
                // đều xác thực qua JWT (không cần session nữa).
                .sessionManagement(sm -> sm
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

                // Phân quyền theo URL
                .authorizeHttpRequests(auth -> auth
                        // Cho phép tất cả truy cập vào endpoints công khai
                        .requestMatchers(
                                "/",
                                "/tours/**",
                                "/reviews/**",
                                "/auth/**",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/uploads/**",
                                "/error/**"
                        ).permitAll()

                        // Chỉ cho phép ROLE_ADMIN truy cập /admin/**
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // Yêu cầu xác thực cho tất cả request còn lại
                        .anyRequest().authenticated()
                )

                // JWT filter chạy trước UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthenticationFilter(),
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
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/auth/login")
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(customOAuth2UserService)
                        )
                        .successHandler(oAuth2SuccessHandler)
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
