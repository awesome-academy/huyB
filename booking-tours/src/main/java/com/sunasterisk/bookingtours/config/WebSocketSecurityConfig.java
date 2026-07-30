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

    @Bean
    AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        messages
                .simpSubscribeDestMatchers("/user/queue/**", "/topic/**").authenticated()
                .anyMessage().authenticated();
        return messages.build();
    }
}
