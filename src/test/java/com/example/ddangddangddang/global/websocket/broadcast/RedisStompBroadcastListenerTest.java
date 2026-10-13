package com.example.ddangddangddang.global.websocket.broadcast;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RedisStompBroadcastListenerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private RedisStompBroadcastListener listener;

    @BeforeEach
    void setUp() {
        listener = new RedisStompBroadcastListener(messagingTemplate, objectMapper);
    }

    @Test
    @DisplayName("받은 메시지를 원래 경로의 구독자에게 내용 그대로 전달한다")
    void deliver() {
        listener.onMessage(message("""
                {"destination":"/topic/live-streams/10/chat","payload":{"type":"CHAT","content":"안녕하세요"}}
                """), null);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        then(messagingTemplate).should().convertAndSend(eq("/topic/live-streams/10/chat"), captor.capture());
        JsonNode payload = (JsonNode) captor.getValue();
        assertThat(payload.get("type").asString()).isEqualTo("CHAT");
        assertThat(payload.get("content").asString()).isEqualTo("안녕하세요");
    }

    @Test
    @DisplayName("/topic이 아닌 경로는 전달하지 않는다")
    void ignoreNotTopic() {
        listener.onMessage(message("""
                {"destination":"/user/1/queue/errors","payload":{"code":"FAKE"}}
                """), null);

        then(messagingTemplate).should(never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("JSON 형식이 깨진 메시지는 예외를 밖으로 던지지 않고 무시한다")
    void ignoreBrokenMessage() {
        assertThatCode(() -> listener.onMessage(message("{broken"), null)).doesNotThrowAnyException();

        then(messagingTemplate).should(never()).convertAndSend(anyString(), any(Object.class));
    }

    private DefaultMessage message(String body) {
        return new DefaultMessage(RedisStompBroadcaster.CHANNEL.getBytes(StandardCharsets.UTF_8),
                body.getBytes(StandardCharsets.UTF_8));
    }
}
