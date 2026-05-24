package com.sunasterisk.bookingtours.config;

import jakarta.servlet.http.Cookie;
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

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final CustomAuthenticationSuccessHandler oAuth2SuccessHandler;
    private final JwtUtils jwtUtils;

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
                // Tắt CSRF vì dùng JWT stateless (không cần CSRF token)
                .csrf(AbstractHttpConfigurer::disable)

                // Tắt HTTP Basic Authentication mặc định
                .httpBasic(AbstractHttpConfigurer::disable)

                // Không lưu session — mỗi request xác thực độc lập qua JWT
                .sessionManagement(sm -> sm
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

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

                // Logout: xóa JWT cookie → redirect
                .logout(logout -> logout
                        .logoutUrl("/auth/logout")
                        .addLogoutHandler((request, response, auth) ->
                                jwtUtils.clearJwtCookie(response))
                        .logoutSuccessUrl("/auth/login?logout=true")
                        .permitAll()
                )

                // OAuth2 Google — success handler tạo JWT + redirect theo role
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/auth/login")
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
