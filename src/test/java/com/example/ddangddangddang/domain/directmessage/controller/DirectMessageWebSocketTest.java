package com.example.ddangddangddang.domain.directmessage.controller;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.directmessage.dto.DmMessageSendRequest;
import com.example.ddangddangddang.domain.directmessage.repository.DmRateLimitRepository;
import com.example.ddangddangddang.domain.directmessage.service.DirectMessageService;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.security.jwt.JwtProvider;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 메시지를 보내면 상대가 /user/queue/dm 구독으로 실제로 받는지, 관계없는 회원은 받지 않는지 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DirectMessageWebSocketTest {

    private static final long TIMEOUT_SECONDS = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DirectMessageService directMessageService;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    // 도배 제한(Redis)은 통과시킨다. (횟수 0)
    @MockitoBean
    private DmRateLimitRepository dmRateLimitRepository;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @AfterEach
    void tearDown() {
        stompClient.stop();
    }

    @Test
    @DisplayName("보낸 메시지를 상대가 /user/queue/dm으로 받고, 대화에 참여하지 않은 회원은 받지 않는다")
    void receiverGetsMessage() throws Exception {
        User sender = user("보낸이");
        User receiver = user("받는이");
        User stranger = user("남");
        Long roomId = directMessageService.getOrCreateRoom(sender.getId(), receiver.getId()).roomId();

        BlockingQueue<Map<String, Object>> receiverQueue = subscribe(connect(receiver), "/user/queue/dm");
        BlockingQueue<Map<String, Object>> strangerQueue = subscribe(connect(stranger), "/user/queue/dm");

        // 구독 등록 전에 보낸 실시간 전달은 받지 못하므로(메시지는 저장됨), 받을 때까지 새 메시지로 다시 보낸다.
        Map<String, Object> received = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (received == null && System.nanoTime() < deadline) {
            directMessageService.send(sender.getId(), roomId,
                    new DmMessageSendRequest(UUID.randomUUID().toString(), "실시간으로 가나요?"));
            received = receiverQueue.poll(200, TimeUnit.MILLISECONDS);
        }

        assertThat(received).isNotNull();
        assertThat(received.get("type")).isEqualTo("MESSAGE");
        assertThat(((Number) received.get("roomId")).longValue()).isEqualTo(roomId);
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) received.get("message");
        assertThat(((Number) message.get("senderId")).longValue()).isEqualTo(sender.getId());
        assertThat(message.get("content")).isEqualTo("실시간으로 가나요?");
        assertThat(strangerQueue.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    private User user(String name) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.create(name + suffix + "@test.com", "encoded", name + suffix));
    }

    private StompSession connect(User user) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(user));
        return stompClient.connectAsync("ws://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {
                        })
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination) {
        BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((Map<String, Object>) payload);
            }
        });
        return queue;
    }
}
