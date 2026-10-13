package com.example.ddangddangddang.domain.livechat.controller;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.livechat.repository.ChatRateLimitRepository;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.infra.MediaServerClient;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.livestream.service.LiveStreamService;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.entity.UserRole;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LiveChatControllerTest {

    private static final long TIMEOUT_SECONDS = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private LiveStreamRepository liveStreamRepository;

    @Autowired
    private LiveStreamService liveStreamService;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @MockitoBean
    private ChatRateLimitRepository chatRateLimitRepository;

    @MockitoBean
    private MediaServerClient mediaServerClient;

    private WebSocketStompClient stompClient;

    // 서버가 보낸 ERROR 프레임의 message 헤더(에러 코드)
    private final BlockingQueue<String> errorCodes = new LinkedBlockingQueue<>();

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
    @DisplayName("채팅을 보내면 같은 방송 구독자에게 토큰의 회원 정보와 함께 전달된다")
    void sendChat() throws Exception {
        Long liveStreamId = liveStream(99L, true);
        StompSession session = connect(bearer(accessToken(1L)));
        BlockingQueue<Map<String, Object>> messages = subscribe(session, chatTopic(liveStreamId));

        Map<String, Object> message = sendUntilReceived(session, chatApp(liveStreamId), Map.of("content", "  안녕하세요  "), messages);
        assertThat(message).isNotNull();
        assertThat(message.get("type")).isEqualTo("CHAT");
        assertThat(((Number) message.get("liveStreamId")).longValue()).isEqualTo(liveStreamId);
        assertThat(message.get("senderId")).isEqualTo(1);
        assertThat(message.get("nickname")).isEqualTo("회원1");
        assertThat(message.get("content")).isEqualTo("안녕하세요");
    }

    @Test
    @DisplayName("다른 방송의 채팅은 전달되지 않는다")
    void otherStream() throws Exception {
        Long liveStreamId = liveStream(99L, true);
        Long otherLiveStreamId = liveStream(98L, true);
        StompSession session = connect(bearer(accessToken(1L)));
        BlockingQueue<Map<String, Object>> messages = subscribe(session, chatTopic(liveStreamId));

        session.send(chatApp(otherLiveStreamId), Map.of("content", "다른 방송"));

        assertThat(messages.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    @DisplayName("빈 메시지를 보내면 보낸 사람에게만 COMMON_001 에러가 전달된다")
    void blankContent() throws Exception {
        Long liveStreamId = liveStream(99L, true);
        StompSession session = connect(bearer(accessToken(1L)));
        BlockingQueue<Map<String, Object>> messages = subscribe(session, chatTopic(liveStreamId));
        BlockingQueue<Map<String, Object>> errors = subscribe(session, "/user/queue/errors");

        Map<String, Object> error = sendUntilReceived(session, chatApp(liveStreamId), Map.of("content", " "), errors);
        assertThat(error).isNotNull();
        assertThat(error.get("code")).isEqualTo("COMMON_001");
        assertThat(messages.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    @DisplayName("방송 중이 아닌 방송에 채팅을 보내면 LIVESTREAM_004 에러가 전달된다")
    void notLive() throws Exception {
        Long liveStreamId = liveStream(99L, false);
        StompSession session = connect(bearer(accessToken(1L)));
        BlockingQueue<Map<String, Object>> messages = subscribe(session, chatTopic(liveStreamId));
        BlockingQueue<Map<String, Object>> errors = subscribe(session, "/user/queue/errors");

        Map<String, Object> error = sendUntilReceived(session, chatApp(liveStreamId), Map.of("content", "안녕하세요"), errors);
        assertThat(error).isNotNull();
        assertThat(error.get("code")).isEqualTo("LIVESTREAM_004");
        assertThat(messages.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    @DisplayName("너무 빠르게 보내면 LIVECHAT_001 에러가 전달되고 메시지는 전송되지 않는다")
    void tooManyMessages() throws Exception {
        given(chatRateLimitRepository.increment(anyLong(), any())).willReturn(6L);
        Long liveStreamId = liveStream(99L, true);
        StompSession session = connect(bearer(accessToken(1L)));
        BlockingQueue<Map<String, Object>> messages = subscribe(session, chatTopic(liveStreamId));
        BlockingQueue<Map<String, Object>> errors = subscribe(session, "/user/queue/errors");

        Map<String, Object> error = sendUntilReceived(session, chatApp(liveStreamId), Map.of("content", "도배"), errors);
        assertThat(error).isNotNull();
        assertThat(error.get("code")).isEqualTo("LIVECHAT_001");
        assertThat(messages.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    @DisplayName("채팅 금지 중이면 남은 시간을 담은 LIVECHAT_002 에러가 전달되고 메시지는 전송되지 않는다")
    void muted() throws Exception {
        given(chatRateLimitRepository.getMuteRemaining(anyLong())).willReturn(Duration.ofSeconds(23));
        Long liveStreamId = liveStream(99L, true);
        StompSession session = connect(bearer(accessToken(1L)));
        BlockingQueue<Map<String, Object>> messages = subscribe(session, chatTopic(liveStreamId));
        BlockingQueue<Map<String, Object>> errors = subscribe(session, "/user/queue/errors");

        Map<String, Object> error = sendUntilReceived(session, chatApp(liveStreamId), Map.of("content", "안녕하세요"), errors);
        assertThat(error).isNotNull();
        assertThat(error.get("code")).isEqualTo("LIVECHAT_002");
        assertThat(error.get("message")).isEqualTo("도배로 채팅이 금지된 상태입니다. 23초 후에 다시 보낼 수 있습니다.");
        assertThat(messages.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    @DisplayName("방송이 종료되면 구독자에게 종료 안내 SYSTEM 메시지가 전달된다")
    void streamEnded() throws Exception {
        Long sellerId = 99L;
        Long liveStreamId = liveStream(sellerId, true);
        StompSession session = connect(bearer(accessToken(1L)));
        BlockingQueue<Map<String, Object>> messages = subscribe(session, chatTopic(liveStreamId));
        // 채팅이 도착하면 구독이 등록된 것이므로 그 뒤에 종료한다.
        assertThat(sendUntilReceived(session, chatApp(liveStreamId), Map.of("content", "안녕하세요"), messages)).isNotNull();

        liveStreamService.end(liveStreamId, sellerId);

        // 위에서 여러 번 보낸 채팅이 늦게 도착할 수 있으므로 SYSTEM 메시지가 올 때까지 건너뛴다.
        Map<String, Object> message = pollUntilType(messages, "SYSTEM");
        assertThat(message).isNotNull();
        assertThat(message.get("type")).isEqualTo("SYSTEM");
        assertThat(message.get("senderId")).isNull();
        assertThat(message.get("content")).isEqualTo("방송이 종료되었습니다.");
    }

    @Test
    @DisplayName("토큰 없이 연결하면 AUTH_001 에러로 연결이 거부된다")
    void connectWithoutToken() throws Exception {
        connectAsync(null);

        assertThat(errorCodes.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("AUTH_001");
    }

    @Test
    @DisplayName("잘못된 토큰으로 연결하면 AUTH_003 에러로 연결이 거부된다")
    void connectWithInvalidToken() throws Exception {
        connectAsync(bearer("invalid-token"));

        assertThat(errorCodes.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("AUTH_003");
    }

    @Test
    @DisplayName("로그아웃한 토큰으로 연결하면 AUTH_004 에러로 연결이 거부된다")
    void connectWithLoggedOutToken() throws Exception {
        given(tokenBlacklistRepository.exists(anyString())).willReturn(true);

        connectAsync(bearer(accessToken(1L)));

        assertThat(errorCodes.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("AUTH_004");
    }

    @Test
    @DisplayName("/topic으로 직접 보내면 COMMON_005 에러로 연결이 끊긴다")
    void sendToTopicDirectly() throws Exception {
        StompSession session = connect(bearer(accessToken(1L)));
        BlockingQueue<Map<String, Object>> messages = subscribe(session, "/topic/live-streams/10/chat");

        session.send("/topic/live-streams/10/chat", Map.of("content", "사칭 메시지"));

        assertThat(errorCodes.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("COMMON_005");
        assertThat(messages.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    @DisplayName("허용되지 않은 경로를 구독하면 COMMON_005 에러로 연결이 끊긴다")
    void subscribeInvalidDestination() throws Exception {
        StompSession session = connect(bearer(accessToken(1L)));

        subscribe(session, "/topic/anything");

        assertThat(errorCodes.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("COMMON_005");
    }

    private StompSession connect(String authorization) throws Exception {
        return connectAsync(authorization).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private CompletableFuture<StompSession> connectAsync(String authorization) {
        StompHeaders connectHeaders = new StompHeaders();
        if (authorization != null) {
            connectHeaders.add(HttpHeaders.AUTHORIZATION, authorization);
        }
        return stompClient.connectAsync("ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(), connectHeaders, new ErrorCollectingHandler());
    }

    /**
     * SUBSCRIBE와 SEND는 서버에서 서로 다른 스레드로 처리되어, 구독 등록 전에 메시지가 처리될 수 있다.
     * 받을 때까지 다시 보내서 그 경쟁 때문에 테스트가 가끔 실패하지 않게 한다.
     */
    private Map<String, Object> sendUntilReceived(StompSession session, String destination, Object payload,
                                                  BlockingQueue<Map<String, Object>> queue) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            session.send(destination, payload);
            Map<String, Object> received = queue.poll(200, TimeUnit.MILLISECONDS);
            if (received != null) {
                return received;
            }
        }
        return null;
    }

    private Map<String, Object> pollUntilType(BlockingQueue<Map<String, Object>> queue, String type)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            Map<String, Object> received = queue.poll(200, TimeUnit.MILLISECONDS);
            if (received != null && type.equals(received.get("type"))) {
                return received;
            }
        }
        return null;
    }

    private Long liveStream(Long sellerId, boolean live) {
        LiveStream liveStream = LiveStream.create(sellerId, "테스트 방송");
        if (live) {
            liveStream.start(sellerId);
        }
        return liveStreamRepository.save(liveStream).getId();
    }

    private String chatTopic(Long liveStreamId) {
        return "/topic/live-streams/" + liveStreamId + "/chat";
    }

    private String chatApp(Long liveStreamId) {
        return "/app/live-streams/" + liveStreamId + "/chat";
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

    // 세션 핸들러의 handleFrame은 ERROR 프레임을 받을 때 호출된다.
    private class ErrorCollectingHandler extends StompSessionHandlerAdapter {

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return Map.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            errorCodes.add(headers.getFirst("message"));
        }

    }

    private String accessToken(Long userId) {
        User user = User.create("user" + userId + "@test.com", "encoded", "회원" + userId);
        ReflectionTestUtils.setField(user, "id", userId);
        ReflectionTestUtils.setField(user, "role", UserRole.USER);
        return jwtProvider.createAccessToken(user);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
