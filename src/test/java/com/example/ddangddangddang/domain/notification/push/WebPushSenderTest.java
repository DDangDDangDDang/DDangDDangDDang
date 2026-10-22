package com.example.ddangddangddang.domain.notification.push;

import com.example.ddangddangddang.domain.notification.entity.Notification;
import com.example.ddangddangddang.domain.notification.entity.NotificationType;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class WebPushSenderTest {

    private final PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);
    private final WebPushProperties properties = new WebPushProperties(true, null, null, "mailto:admin@ddang.test", null, null);
    private final VapidKeys vapidKeys = new VapidKeys(properties);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("구독한 기기마다 VAPID 서명·암호화 본문·TTL을 붙여 보내고, 410이면 그 구독을 지운다")
    void sendsEncryptedPushAndRemovesGoneSubscription() throws Exception {
        BlockingQueue<Map<String, String>> received = new ArrayBlockingQueue<>(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/push", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            received.add(Map.of(
                    "authorization", exchange.getRequestHeaders().getFirst("Authorization"),
                    "ttl", exchange.getRequestHeaders().getFirst("TTL"),
                    "encoding", exchange.getRequestHeaders().getFirst("Content-Encoding"),
                    "length", String.valueOf(body.length)));
            exchange.sendResponseHeaders(410, -1);
            exchange.close();
        });
        server.start();
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/push/abc";

        KeyPair browser = WebPushCrypto.generateKeyPair();
        PushSubscription subscription = PushSubscription.create(5L, endpoint,
                WebPushCrypto.base64Url(WebPushCrypto.encodePublicKey((ECPublicKey) browser.getPublic())), WebPushCrypto.base64Url(new byte[16]));
        given(repository.findByUserIdIn(List.of(5L))).willReturn(List.of(subscription));
        Notification notification = Notification.auction(5L, NotificationType.AUCTION_OUTBID, 7L, "필름 카메라", 21_000L);
        ReflectionTestUtils.setField(notification, "id", 99L);

        WebPushSender sender = new WebPushSender(repository, vapidKeys, properties, objectMapper, HttpClient.newHttpClient(),
                Clock.fixed(Instant.parse("2026-10-22T00:00:00Z"), ZoneOffset.UTC));
        sender.send(List.of(notification));

        Map<String, String> request = received.poll(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.get("authorization")).startsWith("vapid t=").endsWith(", k=" + vapidKeys.publicKey());
        assertThat(request.get("ttl")).isEqualTo("1800");
        assertThat(request.get("encoding")).isEqualTo("aes128gcm");
        // 헤더(16+4+1+65) + 본문 + 구분 1바이트 + 태그 16바이트
        byte[] payload = sender.payload(notification);
        assertThat(Integer.parseInt(request.get("length"))).isEqualTo(86 + payload.length + 1 + 16);
        verify(repository, timeout(5000)).deleteExpired(subscription.getEndpointHash());

        assertThat(new String(payload, StandardCharsets.UTF_8))
                .contains("\"title\":\"더 높은 입찰이 들어왔어요\"")
                .contains("\"link\":\"/auctions/7\"")
                .contains("\"tag\":\"notification-99\"");
    }
}
