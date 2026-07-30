package com.sunasterisk.bookingtours.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Cấu hình WebSocket với STOMP protocol.
 * - Endpoint /ws: client kết nối (SockJS fallback cho trình duyệt cũ)
 * - Simple in-memory broker: /topic (broadcast), /user/queue (per-user)
 * - Application prefix /app: client gửi message đến server-side @MessageMapping
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Client subscribe /topic/xxx (broadcast) hoặc /user/queue/xxx (per-user)
        registry.enableSimpleBroker("/topic", "/user/queue");
        // Prefix cho message gửi từ client đến server-side @MessageMapping
        registry.setApplicationDestinationPrefixes("/app");
        // Prefix để route message tới user cụ thể khi dùng convertAndSendToUser
        registry.setUserDestinationPrefix("/user");
    }
}
