package com.example.ddangddangddang.global.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket 설정.
 * - 연결: ws://{host}/ws, CONNECT 헤더에 Authorization: Bearer {accessToken}
 * - 구독: /topic/... (전체 전송), /user/queue/... (나에게만 전송)
 * - 전송: /app/... (서버의 @MessageMapping으로 전달)
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final StompErrorHandler stompErrorHandler;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 인증은 쿠키가 아니라 CONNECT 프레임의 토큰으로 하므로 다른 출처의 페이지가 연결해도 사용자 권한을 도용할 수 없다.
        // TODO: 프론트 도메인이 정해지면 허용 출처를 좁힌다.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
        registry.setErrorHandler(stompErrorHandler);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 서버 1대 기준 내장 브로커. 서버가 여러 대가 되면 Redis Pub/Sub으로 메시지를 공유한다.
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
