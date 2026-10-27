package com.example.ddangddangddang.global.websocket.broadcast;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * 서버가 시작되면 Redis 브로드캐스트 채널을 구독한다.
 */
@Configuration
@ConditionalOnProperty(name = StompBroadcaster.PROPERTY, havingValue = "redis", matchIfMissing = true)
public class RedisBroadcastConfig {

    @Bean
    public RedisMessageListenerContainer stompBroadcastListenerContainer(
            RedisConnectionFactory connectionFactory,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                new RedisStompBroadcastListener(messagingTemplate, objectMapper),
                new ChannelTopic(RedisStompBroadcaster.CHANNEL));
        return container;
    }
}
