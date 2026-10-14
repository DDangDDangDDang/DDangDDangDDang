package com.example.ddangddangddang.global.websocket.broadcast;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 구독자에게 직접 보내지 않고 Redis 채널에 발행한다.
 * 이 서버를 포함한 모든 서버가 채널을 구독하고 있다가 자기 구독자에게 전달한다. (RedisStompBroadcastListener)
 * 이 서버의 구독자에게도 직접 보내면 Redis로 한 번 더 받아 중복되므로, 반드시 Redis를 거치게 한다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = StompBroadcaster.PROPERTY, havingValue = "redis", matchIfMissing = true)
public class RedisStompBroadcaster implements StompBroadcaster {

    public static final String CHANNEL = "stomp:broadcast";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void broadcast(String destination, Object payload) {
        publish(StompBroadcastMessage.toTopic(destination, objectMapper.valueToTree(payload)));
    }

    /**
     * 받는 사용자가 어느 서버에 연결돼 있는지 모르므로 모든 서버에 보내고,
     * 각 서버는 그 사용자가 자기에게 연결돼 있을 때만 전달한다. (연결돼 있지 않으면 아무 일도 없음)
     */
    @Override
    public void sendToUser(Long userId, String destination, Object payload) {
        publish(StompBroadcastMessage.toUser(String.valueOf(userId), destination, objectMapper.valueToTree(payload)));
    }

    private void publish(StompBroadcastMessage message) {
        redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(message));
    }
}
