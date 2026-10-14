package com.example.ddangddangddang.domain.livechat.moderation;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.livechat.repository.ChatRateLimitRepository;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.infra.MediaServerClient;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 채팅 모더레이션 전체 흐름을 실제 WebSocket으로 확인한다.
 * AI 대신 "카톡"이 들어간 메시지를 표시하는 가짜 검토기를 쓴다. (실제 모델 판단은 API 키가 있을 때 따로 확인)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "chat.moderation.ai.interval=200ms")
class ChatModerationIntegrationTest {

    private static final long TIMEOUT_SECONDS = 5;
    private static final Long SELLER_ID = 1L;
    private static final Long VIEWER_ID = 2L;
    private static final Long OTHER_VIEWER_ID = 3L;

    @TestConfiguration
    static class FakeModeratorConfig {

        @Bean
        @Primary
        ChatModerator fakeModerator() {
            return new ChatModerator() {
                @Override
                public boolean isEnabled() {
                    return true;
                }

                @Override
                public List<ModerationFlag> review(List<ChatForReview> messages) {
                    return messages.stream()
                            .filter(m -> m.content().contains("카톡"))
                            .map(m -> new ModerationFlag(m.messageId(), ModerationCategory.EXTERNAL_TRADE,
                                    "외부 메신저로 직거래를 유도합니다."))
                            .toList();
                }
            };
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private LiveStreamRepository liveStreamRepository;

    @Autowired
    private SimpUserRegistry userRegistry;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @MockitoBean
    private ChatRateLimitRepository chatRateLimitRepository;

    @MockitoBean
    private MediaServerClient mediaServerClient;

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
    @DisplayName("AI가 표시한 채팅은 몇 초 뒤 판매자에게만 알림으로 오고, 메시지 자체는 시청자 모두에게 그대로 전달된다")
    void alertSellerOnly() throws Exception {
        Long liveStreamId = liveStream();
        String chatTopic = "/topic/live-streams/" + liveStreamId + "/chat";

        StompSession seller = connect(SELLER_ID);
        BlockingQueue<Map<String, Object>> sellerAlerts = subscribe(seller, "/user/queue/moderation");
        StompSession viewer = connect(VIEWER_ID);
        BlockingQueue<Map<String, Object>> viewerChats = subscribe(viewer, chatTopic);
        BlockingQueue<Map<String, Object>> viewerAlerts = subscribe(viewer, "/user/queue/moderation");
        StompSession other = connect(OTHER_VIEWER_ID);
        BlockingQueue<Map<String, Object>> otherAlerts = subscribe(other, "/user/queue/moderation");
        awaitSubscribed(chatTopic);
        awaitSubscribed("/user/queue/moderation");

        viewer.send("/app/live-streams/" + liveStreamId + "/chat", Map.of("content", "카톡 아디 seller77 로 따로 연락주세요"));

        Map<String, Object> chat = viewerChats.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(chat).isNotNull();
        assertThat(chat.get("messageId")).isNotNull();

        Map<String, Object> alert = sellerAlerts.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(alert).isNotNull();
        assertThat(alert.get("messageId")).isEqualTo(chat.get("messageId"));
        assertThat(((Number) alert.get("liveStreamId")).longValue()).isEqualTo(liveStreamId);
        assertThat(((Number) alert.get("senderId")).longValue()).isEqualTo(VIEWER_ID);
        assertThat(alert.get("nickname")).isEqualTo("회원" + VIEWER_ID);
        assertThat(alert.get("category")).isEqualTo("EXTERNAL_TRADE");
        assertThat(alert.get("categoryLabel")).isEqualTo("외부 거래 유도");
        assertThat(alert.get("reason")).isEqualTo("외부 메신저로 직거래를 유도합니다.");

        // 판매자가 아닌 사람에게는 알림이 가지 않는다.
        assertThat(viewerAlerts.poll(500, TimeUnit.MILLISECONDS)).isNull();
        assertThat(otherAlerts.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @DisplayName("문제없는 채팅은 알림이 가지 않는다")
    void noAlertForNormalChat() throws Exception {
        Long liveStreamId = liveStream();
        StompSession seller = connect(SELLER_ID);
        BlockingQueue<Map<String, Object>> sellerAlerts = subscribe(seller, "/user/queue/moderation");
        StompSession viewer = connect(VIEWER_ID);
        awaitSubscribed("/user/queue/moderation");

        viewer.send("/app/live-streams/" + liveStreamId + "/chat", Map.of("content", "사이즈 몇이에요?"));

        assertThat(sellerAlerts.poll(1500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @DisplayName("전화번호가 들어간 채팅은 전송 전에 LIVECHAT_003으로 거부되고 시청자에게 전달되지 않는다")
    void phoneNumberRejected() throws Exception {
        Long liveStreamId = liveStream();
        String chatTopic = "/topic/live-streams/" + liveStreamId + "/chat";
        StompSession viewer = connect(VIEWER_ID);
        BlockingQueue<Map<String, Object>> chats = subscribe(viewer, chatTopic);
        BlockingQueue<Map<String, Object>> errors = subscribe(viewer, "/user/queue/errors");
        awaitSubscribed("/user/queue/errors");

        viewer.send("/app/live-streams/" + liveStreamId + "/chat", Map.of("content", "연락주세요 010-1234-5678"));

        Map<String, Object> error = errors.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat(error.get("code")).isEqualTo("LIVECHAT_003");
        assertThat(error.get("message")).isEqualTo("전화번호는 채팅으로 보낼 수 없습니다.");
        assertThat(chats.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    private Long liveStream() {
        LiveStream liveStream = LiveStream.create(SELLER_ID, "테스트 방송");
        liveStream.start(SELLER_ID);
        return liveStreamRepository.save(liveStream).getId();
    }

    private StompSession connect(Long userId) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(userId));
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

    // 구독이 서버에 등록된 뒤에 보내야 메시지가 유실된 것처럼 보이지 않는다.
    private void awaitSubscribed(String destination) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (!userRegistry.findSubscriptions(s -> destination.equals(s.getDestination())).isEmpty()) {
                Thread.sleep(100);
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("구독이 등록되지 않았습니다: " + destination);
    }

    private String accessToken(Long userId) {
        User user = User.create("user" + userId + "@test.com", "encoded", "회원" + userId);
        ReflectionTestUtils.setField(user, "id", userId);
        ReflectionTestUtils.setField(user, "role", UserRole.USER);
        return jwtProvider.createAccessToken(user);
    }
}
