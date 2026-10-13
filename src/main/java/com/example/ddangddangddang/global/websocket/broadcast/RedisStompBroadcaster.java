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
        StompBroadcastMessage message = new StompBroadcastMessage(destination, objectMapper.valueToTree(payload));
        redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(message));
    }
}
