package com.example.ddangddangddang.global.websocket.broadcast;

import com.example.ddangddangddang.DDangDDangDDangApplication;
import com.example.ddangddangddang.domain.livechat.service.LiveChatService;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.entity.UserRole;
import com.example.ddangddangddang.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 서버가 여러 대일 때 Redis Pub/Sub으로 채팅이 모든 서버의 구독자에게 전달되는지 확인한다.
 * Redis 컨테이너 하나에 서버 두 대를 띄운다. 두 서버는 운영처럼 같은 DB(H2)와 같은 Redis를 쓴다.
 * Docker가 실행 중이어야 한다.
 */
@Testcontainers
class RedisStompBroadcastTest {

    private static final long TIMEOUT_SECONDS = 5;

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4")).withExposedPorts(6379);

    private static ConfigurableApplicationContext server1;
    private static ConfigurableApplicationContext server2;

    private WebSocketStompClient stompClient;

    @BeforeAll
    static void startServers() {
        server1 = startServer();
        server2 = startServer();
    }

    @AfterAll
    static void stopServers() {
        if (server1 != null) {
            server1.close();
        }
        if (server2 != null) {
            server2.close();
        }
    }

    private static ConfigurableApplicationContext startServer() {
        return new SpringApplicationBuilder(DDangDDangDDangApplication.class).run(
                "--server.port=0",
                "--websocket.broadcast=redis",
                "--spring.data.redis.host=" + REDIS.getHost(),
                "--spring.data.redis.port=" + REDIS.getMappedPort(6379),
                // 다른 테스트가 쓰는 H2(testdb)와 섞이지 않게 별도 DB를 쓴다.
                // 두 번째 서버가 시작할 때 테이블을 지우지 않도록 create-drop 대신 create를 쓴다. (데이터는 서버를 다 띄운 뒤에 넣는다)
                "--spring.datasource.url=jdbc:h2:mem:broadcast-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "--spring.jpa.hibernate.ddl-auto=create"
        );
    }

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
    @DisplayName("서버 1에서 보낸 채팅을 서버 2에 연결된 시청자도 받고, 서버 1의 구독자는 중복 없이 한 번만 받는다")
    void chatAcrossServers() throws Exception {
        Long liveStreamId = liveStream();
        String topic = chatTopic(liveStreamId);

        StompSession sender = connect(server1, 1L);
        BlockingQueue<Map<String, Object>> server1Messages = subscribe(sender, topic);
        StompSession viewer = connect(server2, 2L);
        BlockingQueue<Map<String, Object>> server2Messages = subscribe(viewer, topic);
        awaitSubscribed(server1, topic);
        awaitSubscribed(server2, topic);

        // 구독 등록과 전송이 서버에서 서로 다른 스레드로 처리될 수 있어서, 받을 때까지 내용을 바꿔 가며 다시 보낸다.
        // 실제 Redis의 도배 제한(5초에 5개)에 걸리지 않게 1초 간격으로 보낸다.
        Map<String, Object> received = null;
        String content = null;
        for (int attempt = 1; attempt <= 4 && received == null; attempt++) {
            content = "안녕하세요-" + attempt;
            sender.send("/app/live-streams/" + liveStreamId + "/chat", Map.of("content", content));
            received = server2Messages.poll(1, TimeUnit.SECONDS);
        }

        assertThat(received).isNotNull();
        assertThat(received.get("type")).isEqualTo("CHAT");
        assertThat(received.get("senderId")).isEqualTo(1);
        assertThat(received.get("nickname")).isEqualTo("회원1");
        assertThat(received.get("content")).isEqualTo(content);

        // 보낸 서버의 구독자도 같은 메시지를 정확히 한 번 받는다. (직접 전송 + Redis 수신으로 두 번 받지 않는다)
        assertThat(countContent(server1Messages, content)).isEqualTo(1);
    }

    @Test
    @DisplayName("서버 2에서 보낸 SYSTEM 메시지를 서버 1에 연결된 시청자가 받는다")
    void systemMessageAcrossServers() throws Exception {
        Long liveStreamId = liveStream();
        String topic = chatTopic(liveStreamId);

        StompSession viewer = connect(server1, 2L);
        BlockingQueue<Map<String, Object>> server1Messages = subscribe(viewer, topic);
        awaitSubscribed(server1, topic);

        Map<String, Object> received = null;
        for (int attempt = 1; attempt <= 4 && received == null; attempt++) {
            server2.getBean(LiveChatService.class).sendSystemMessage(liveStreamId, "방송이 종료되었습니다.");
            received = server1Messages.poll(1, TimeUnit.SECONDS);
        }

        assertThat(received).isNotNull();
        assertThat(received.get("type")).isEqualTo("SYSTEM");
        assertThat(received.get("senderId")).isNull();
        assertThat(received.get("content")).isEqualTo("방송이 종료되었습니다.");
    }

    @Test
    @DisplayName("서버 1에서 특정 사용자에게 보낸 메시지를 서버 2에 연결된 그 사용자만 받는다")
    void sendToUserAcrossServers() throws Exception {
        StompSession seller = connect(server2, 99L);
        BlockingQueue<Map<String, Object>> sellerQueue = subscribe(seller, "/user/queue/moderation");
        StompSession otherUser = connect(server2, 2L);
        BlockingQueue<Map<String, Object>> otherQueue = subscribe(otherUser, "/user/queue/moderation");
        awaitSubscribed(server2, "/user/queue/moderation");

        Map<String, Object> received = null;
        for (int attempt = 1; attempt <= 4 && received == null; attempt++) {
            server1.getBean(StompBroadcaster.class)
                    .sendToUser(99L, "/queue/moderation", Map.of("messageId", "m-" + attempt));
            received = sellerQueue.poll(1, TimeUnit.SECONDS);
        }

        assertThat(received).isNotNull();
        assertThat(received.get("messageId")).asString().startsWith("m-");
        assertThat(otherQueue.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    private Long liveStream() {
        LiveStreamRepository repository = server1.getBean(LiveStreamRepository.class);
        // 판매자당 종료되지 않은 방송은 하나라서, 앞선 테스트가 남긴 방송을 먼저 끝낸다.
        repository.findByActiveSellerId(99L).ifPresent(previous -> {
            previous.end(99L);
            repository.save(previous);
        });
        LiveStream liveStream = LiveStream.create(99L, "테스트 방송");
        liveStream.start(99L);
        return repository.save(liveStream).getId();
    }

    private String chatTopic(Long liveStreamId) {
        return "/topic/live-streams/" + liveStreamId + "/chat";
    }

    private StompSession connect(ConfigurableApplicationContext server, Long userId) throws Exception {
        String port = server.getEnvironment().getProperty("local.server.port");
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

    // 해당 서버가 구독을 받았는지 확인한다. 받기 전에 보내면 메시지가 유실된 것처럼 보이기 때문이다.
    private void awaitSubscribed(ConfigurableApplicationContext server, String destination) throws InterruptedException {
        SimpUserRegistry userRegistry = server.getBean(SimpUserRegistry.class);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (!userRegistry.findSubscriptions(subscription -> destination.equals(subscription.getDestination())).isEmpty()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("구독이 등록되지 않았습니다: " + destination);
    }

    // 1초 동안 들어온 메시지 중 내용이 같은 것의 개수
    private long countContent(BlockingQueue<Map<String, Object>> queue, String content) throws InterruptedException {
        List<Map<String, Object>> messages = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            Map<String, Object> message = queue.poll(100, TimeUnit.MILLISECONDS);
            if (message != null) {
                messages.add(message);
            }
        }
        return messages.stream().filter(message -> content.equals(message.get("content"))).count();
    }

    private String accessToken(Long userId) {
        User user = User.create("user" + userId + "@test.com", "encoded", "회원" + userId);
        ReflectionTestUtils.setField(user, "id", userId);
        ReflectionTestUtils.setField(user, "role", UserRole.USER);
        return server1.getBean(JwtProvider.class).createAccessToken(user);
    }
}
