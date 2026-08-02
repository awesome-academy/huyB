package com.sunasterisk.bookingtours.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

/**
 * Bảo mật WebSocket/STOMP: yêu cầu xác thực cho mọi message.
 * CSRF cho WebSocket được xử lý ở tầng HTTP (SockJS dùng HTTP polling với
 * CookieCsrfTokenRepository → XSRF-TOKEN cookie gửi kèm mọi request).
 * sameOriginDisabled = true để SockJS cross-tab hoạt động đúng.
 */
@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfig {

    /**
     * Định nghĩa rule phân quyền cho từng loại STOMP message.
     * Spring Security 6 dùng AuthorizationManager thay cho cách cũ (configureInbound).
     */
    @Bean
    AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        messages
                // Chỉ user đã đăng nhập mới được subscribe /user/queue/** (per-user)
                // và /topic/** (broadcast) — chặn anonymous listener
                .simpSubscribeDestMatchers("/user/queue/**", "/topic/**").authenticated()
                // Chặn client SEND trực tiếp vào broker destination (/queue/**, /topic/**).
                // Không có @MessageMapping nào tồn tại — mọi server→client push đều đi qua
                // SimpMessagingTemplate (không qua client SEND). Nếu cho phép, attacker có
                // thể gửi fake notification đến session của user khác nếu biết sessionId.
                .simpMessageDestMatchers("/queue/**", "/topic/**").denyAll()
                // Mọi loại message khác (CONNECT, SEND đến /app/**, DISCONNECT) yêu cầu xác thực
                .anyMessage().authenticated();
        return messages.build();
    }
}
