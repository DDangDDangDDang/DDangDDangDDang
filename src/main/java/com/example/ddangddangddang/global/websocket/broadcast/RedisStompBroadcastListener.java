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
    // 특정 사용자에게 보내는 메시지는 /queue/... 경로만 허용한다. (사용자는 /user/queue/...로 구독)
    private static final String ALLOWED_USER_DESTINATION_PREFIX = "/queue/";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        // 여기서 예외가 나면 Redis 구독 스레드로 번지므로, 메시지 하나의 실패로 끝나도록 잡아서 기록만 한다.
        try {
            StompBroadcastMessage broadcast = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8), StompBroadcastMessage.class);

            String destination = broadcast.destination();
            if (broadcast.user() != null) {
                if (destination == null || !destination.startsWith(ALLOWED_USER_DESTINATION_PREFIX)) {
                    log.warn("허용되지 않은 경로의 사용자 메시지를 무시합니다. destination={}", destination);
                    return;
                }
                // 이 서버에 그 사용자가 연결돼 있지 않으면 Spring이 아무것도 보내지 않는다.
                messagingTemplate.convertAndSendToUser(broadcast.user(), destination, broadcast.payload());
                return;
            }

            if (destination == null || !destination.startsWith(ALLOWED_DESTINATION_PREFIX)) {
                log.warn("허용되지 않은 경로의 브로드캐스트 메시지를 무시합니다. destination={}", destination);
                return;
            }
            messagingTemplate.convertAndSend(destination, broadcast.payload());
        } catch (Exception e) {
            log.error("Redis 브로드캐스트 메시지 처리 실패", e);
        }
    }
}
