package com.example.ddangddangddang.global.websocket.broadcast;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * Redis 채널로 받은 메시지를 이 서버에 연결된 구독자에게 전달한다.
 */
@Slf4j
@RequiredArgsConstructor
public class RedisStompBroadcastListener implements MessageListener {

    // 클라이언트 구독용 경로로만 전달한다. 채널에 잘못된 메시지가 들어와도 다른 경로로 새지 않게 한다.
    private static final String ALLOWED_DESTINATION_PREFIX = "/topic/";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        // 여기서 예외가 나면 Redis 구독 스레드로 번지므로, 메시지 하나의 실패로 끝나도록 잡아서 기록만 한다.
        try {
            StompBroadcastMessage broadcast = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8), StompBroadcastMessage.class);

            if (broadcast.destination() == null || !broadcast.destination().startsWith(ALLOWED_DESTINATION_PREFIX)) {
                log.warn("허용되지 않은 경로의 브로드캐스트 메시지를 무시합니다. destination={}", broadcast.destination());
                return;
            }
            messagingTemplate.convertAndSend(broadcast.destination(), broadcast.payload());
        } catch (Exception e) {
            log.error("Redis 브로드캐스트 메시지 처리 실패", e);
        }
    }
}
