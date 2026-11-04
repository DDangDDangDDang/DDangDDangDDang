package com.example.ddangddangddang.global.websocket;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
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

    // 서버와 브라우저가 10초마다 연결 확인 신호를 주고받는다. (브라우저 쪽 stompjs 기본값과 같음)
    // 없으면 조용한 방송에서 로드밸런서가 유휴 연결을 끊고(idle_timeout), 중간에 죽은 연결도 양쪽이 한동안 알아채지 못한다.
    static final long HEARTBEAT_MILLIS = 10_000;

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final StompErrorHandler stompErrorHandler;

    // WebSocket 연결을 받을 페이지 출처. 운영·개발 서버는 서비스 주소만 넣는다. (APP_WEBSOCKET_ALLOWED_ORIGINS, 쉼표로 여러 개)
    // 인증은 CONNECT 프레임의 토큰으로 해서 다른 출처의 페이지가 사용자 권한을 쓸 수는 없지만, 손님 연결까지 남의 사이트에서 열지 못하게 좁힌다.
    @Value("${app.websocket.allowed-origins:*}")
    private String[] allowedOrigins;

    // 하트비트 전용 스케줄러. 빈으로 등록하지 않아 @Scheduled·@Async가 쓰는 스케줄러에 영향을 주지 않는다.
    private final ThreadPoolTaskScheduler heartbeatScheduler = createHeartbeatScheduler();

    private static ThreadPoolTaskScheduler createHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @PreDestroy
    void shutdownHeartbeatScheduler() {
        heartbeatScheduler.shutdown();
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins);
        registry.setErrorHandler(stompErrorHandler);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 서버 1대 기준 내장 브로커. 서버가 여러 대가 되면 Redis Pub/Sub으로 메시지를 공유한다.
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{HEARTBEAT_MILLIS, HEARTBEAT_MILLIS})
                .setTaskScheduler(heartbeatScheduler);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
