package com.example.ddangddangddang.domain.notification.controller;

import com.example.ddangddangddang.domain.auction.service.AuctionLifecycleService;
import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.category.entity.Category;
import com.example.ddangddangddang.domain.category.repository.CategoryRepository;
import com.example.ddangddangddang.domain.livestream.infra.MediaServerClient;
import com.example.ddangddangddang.domain.notification.dto.NotificationResponse;
import com.example.ddangddangddang.domain.notification.entity.NotificationType;
import com.example.ddangddangddang.domain.notification.event.AuctionClosingSoonNotifier;
import com.example.ddangddangddang.domain.notification.push.PushSubscription;
import com.example.ddangddangddang.domain.notification.push.PushSubscriptionRepository;
import com.example.ddangddangddang.domain.notification.push.WebPushCrypto;
import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.domain.product.repository.ProductRepository;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
import com.example.ddangddangddang.support.SignupFixture;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.security.interfaces.ECPublicKey;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 경매 알림 받기(시작·마감 임박·취소)와 브라우저 알림(웹 푸시) 구독
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuctionAlertControllerTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuctionLifecycleService auctionLifecycleService;

    @Autowired
    private AuctionClosingSoonNotifier closingSoonNotifier;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PushSubscriptionRepository pushSubscriptionRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @MockitoBean
    private MediaServerClient mediaServerClient;

    @MockitoBean
    private VerificationService verificationService;

    @MockitoBean
    private StompBroadcaster stompBroadcaster;

    @Test
    @DisplayName("알림 받기를 한 경매가 시작되고, 마감 10분 전이 되면(한 번만) 알림이 온다")
    void startAndClosingSoon() throws Exception {
        Member seller = signupAndLogin();
        Member viewer = signupAndLogin();
        // 6분짜리 경매: 시작하면 바로 마감 10분 전 구간에 들어간다
        Long auctionId = createAuction(seller, LocalDateTime.now().plusMinutes(1), LocalDateTime.now().plusMinutes(7));

        alert(viewer, auctionId).andExpect(jsonPath("$.data.subscribed").value(true));
        alert(viewer, auctionId).andExpect(jsonPath("$.data.subscribed").value(true));
        mockMvc.perform(get("/api/v1/auctions/{id}/alert", auctionId).header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
                .andExpect(jsonPath("$.data.subscribed").value(true));
        // 내 경매는 알림 받기를 하지 않아도 결과를 알려 준다
        alert(seller, auctionId).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("NOTIFICATION_003"));

        auctionLifecycleService.startAuction(auctionId);
        verify(stompBroadcaster, timeout(5000)).sendToUser(eq(viewer.id()), eq("/queue/notifications"), argThat(message ->
                message instanceof NotificationResponse n && n.type() == NotificationType.AUCTION_STARTED
                        && n.auctionId().equals(auctionId) && n.title().equals("경매가 시작됐어요")
                        && n.link().equals("/auctions/" + auctionId) && n.body().contains("알림 상품")));

        closingSoonNotifier.notifyClosingSoon();
        closingSoonNotifier.notifyClosingSoon();
        verify(stompBroadcaster, after(1000).times(1)).sendToUser(eq(viewer.id()), eq("/queue/notifications"), argThat(message ->
                message instanceof NotificationResponse n && n.type() == NotificationType.AUCTION_CLOSING_SOON && n.auctionId().equals(auctionId)));
        mockMvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
                .andExpect(jsonPath("$.data.content[0].type").value("AUCTION_CLOSING_SOON"))
                .andExpect(jsonPath("$.data.content[0].title").value("경매가 곧 마감돼요"))
                .andExpect(jsonPath("$.data.content[1].type").value("AUCTION_STARTED"));
    }

    @Test
    @DisplayName("알림 받기를 한 경매가 취소되면 알림이 오고, 끝난 경매는 알림을 받을 수 없다. 끄면 받지 않는다")
    void canceled() throws Exception {
        Member seller = signupAndLogin();
        Member viewer = signupAndLogin();
        Member other = signupAndLogin();
        Long auctionId = createAuction(seller, LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2));
        alert(viewer, auctionId);
        alert(other, auctionId);
        mockMvc.perform(delete("/api/v1/auctions/{id}/alert", auctionId).header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(jsonPath("$.data.subscribed").value(false));

        mockMvc.perform(post("/api/v1/auctions/{id}/cancel", auctionId).header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(status().is2xxSuccessful());

        verify(stompBroadcaster, timeout(5000)).sendToUser(eq(viewer.id()), eq("/queue/notifications"), argThat(message ->
                message instanceof NotificationResponse n && n.type() == NotificationType.AUCTION_CANCELED && n.auctionId().equals(auctionId)));
        verify(stompBroadcaster, after(500).never()).sendToUser(eq(other.id()), eq("/queue/notifications"), argThat(message ->
                message instanceof NotificationResponse n && n.type() == NotificationType.AUCTION_CANCELED));
        alert(other, auctionId).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("NOTIFICATION_002"));
        alert(other, 999_999_999L).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("AUCTION_001"));
    }

    @Test
    @DisplayName("브라우저 알림 구독: 알려진 푸시 서비스의 https 주소와 올바른 키만 받고, 같은 브라우저면 지금 회원의 구독으로 바뀐다")
    void pushSubscription() throws Exception {
        Member first = signupAndLogin();
        Member second = signupAndLogin();
        String endpoint = "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID();
        String p256dh = WebPushCrypto.base64Url(WebPushCrypto.encodePublicKey((ECPublicKey) WebPushCrypto.generateKeyPair().getPublic()));
        String auth = WebPushCrypto.base64Url(new byte[16]);

        mockMvc.perform(get("/api/v1/push/public-key").header(HttpHeaders.AUTHORIZATION, first.bearer()))
                .andExpect(jsonPath("$.data.publicKey").isString());
        subscribePush(first, endpoint, p256dh, auth).andExpect(status().isOk());
        subscribePush(second, endpoint, p256dh, auth).andExpect(status().isOk());
        PushSubscription saved = pushSubscriptionRepository.findByEndpointHash(PushSubscription.hash(endpoint)).orElseThrow();
        assertThat(saved.getUserId()).isEqualTo(second.id());

        // 서버가 아무 주소로나 요청을 보내지 않도록: http, 모르는 호스트, 잘못된 키는 거절
        subscribePush(first, "http://fcm.googleapis.com/fcm/send/x", p256dh, auth).andExpect(jsonPath("$.error.code").value("NOTIFICATION_004"));
        subscribePush(first, "https://169.254.169.254/latest", p256dh, auth).andExpect(jsonPath("$.error.code").value("NOTIFICATION_004"));
        subscribePush(first, "https://fcm.googleapis.com.evil.test/x", p256dh, auth).andExpect(jsonPath("$.error.code").value("NOTIFICATION_004"));
        subscribePush(first, endpoint, "not-a-key", auth).andExpect(jsonPath("$.error.code").value("NOTIFICATION_004"));
        subscribePush(first, endpoint, p256dh, WebPushCrypto.base64Url(new byte[8])).andExpect(jsonPath("$.error.code").value("NOTIFICATION_004"));

        // 남의 구독은 지우지 못하고, 내 구독은 지운다
        mockMvc.perform(delete("/api/v1/push/subscriptions").param("endpoint", endpoint).header(HttpHeaders.AUTHORIZATION, first.bearer()))
                .andExpect(status().isNoContent());
        assertThat(pushSubscriptionRepository.findByEndpointHash(PushSubscription.hash(endpoint))).isPresent();
        mockMvc.perform(delete("/api/v1/push/subscriptions").param("endpoint", endpoint).header(HttpHeaders.AUTHORIZATION, second.bearer()))
                .andExpect(status().isNoContent());
        assertThat(pushSubscriptionRepository.findByEndpointHash(PushSubscription.hash(endpoint))).isEmpty();
        mockMvc.perform(get("/api/v1/push/public-key")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("내가 알림 받기를 한 경매를 최근에 받기로 한 것부터 경매 카드 형식으로 보여 주고, 끄면 빠진다")
    void myAlerts() throws Exception {
        Member seller = signupAndLogin();
        Member viewer = signupAndLogin();
        Long first = createAuction(seller, LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2));
        Long second = createAuction(seller, LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(3));
        alert(viewer, first);
        alert(viewer, second);

        mockMvc.perform(get("/api/v1/users/me/auction-alerts").param("size", "1").header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].auctionId").value(second))
                .andExpect(jsonPath("$.data.content[0].productName").value("알림 상품"))
                .andExpect(jsonPath("$.data.content[0].categoryName").value("카메라"))
                .andExpect(jsonPath("$.data.content[0].status").value("READY"))
                .andExpect(jsonPath("$.data.hasNext").value(true));

        mockMvc.perform(delete("/api/v1/auctions/{id}/alert", second).header(HttpHeaders.AUTHORIZATION, viewer.bearer()));
        mockMvc.perform(get("/api/v1/users/me/auction-alerts").header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].auctionId").value(first))
                .andExpect(jsonPath("$.data.hasNext").value(false));
        mockMvc.perform(get("/api/v1/users/me/auction-alerts")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("알림 설정: 기본은 모두 켜짐이고, 끈 묶음의 알림은 알림함에 쌓이지 않고 실시간으로도 오지 않는다. 다시 켜면 받는다")
    void settings() throws Exception {
        Member seller = signupAndLogin();
        Member viewer = signupAndLogin();
        mockMvc.perform(get("/api/v1/notifications/settings").header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data[?(@.enabled == false)]").isEmpty())
                .andExpect(jsonPath("$.data[?(@.category == 'AUCTION_RESULT')].label").value("경매 결과"));

        // 경매 취소 알림을 끈다. (두 번 꺼도 그대로)
        updateSetting(viewer, "AUCTION_CANCELED", false);
        updateSetting(viewer, "AUCTION_CANCELED", false)
                .andExpect(jsonPath("$.data[?(@.category == 'AUCTION_CANCELED')].enabled").value(false))
                .andExpect(jsonPath("$.data[?(@.category == 'AUCTION_STARTED')].enabled").value(true));

        Long auctionId = createAuction(seller, LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2));
        alert(viewer, auctionId);
        mockMvc.perform(post("/api/v1/auctions/{id}/cancel", auctionId).header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(status().is2xxSuccessful());
        verify(stompBroadcaster, after(1000).never()).sendToUser(eq(viewer.id()), eq("/queue/notifications"), argThat(message ->
                message instanceof NotificationResponse n && n.type() == NotificationType.AUCTION_CANCELED));
        mockMvc.perform(get("/api/v1/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
                .andExpect(jsonPath("$.data.count").value(0));

        // 다시 켜면 받는다
        updateSetting(viewer, "AUCTION_CANCELED", true)
                .andExpect(jsonPath("$.data[?(@.enabled == false)]").isEmpty());
        Long another = createAuction(seller, LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2));
        alert(viewer, another);
        mockMvc.perform(post("/api/v1/auctions/{id}/cancel", another).header(HttpHeaders.AUTHORIZATION, seller.bearer()));
        verify(stompBroadcaster, timeout(5000)).sendToUser(eq(viewer.id()), eq("/queue/notifications"), argThat(message ->
                message instanceof NotificationResponse n && n.type() == NotificationType.AUCTION_CANCELED && n.auctionId().equals(another)));

        // 없는 묶음은 400
        updateSetting(viewer, "NOPE", false).andExpect(status().isBadRequest());
    }

    private ResultActions updateSetting(Member member, String category, boolean enabled) throws Exception {
        return mockMvc.perform(put("/api/v1/notifications/settings/{category}", category).header(HttpHeaders.AUTHORIZATION, member.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\": " + enabled + "}"));
    }

    private ResultActions alert(Member member, Long auctionId) throws Exception {
        return mockMvc.perform(put("/api/v1/auctions/{id}/alert", auctionId).header(HttpHeaders.AUTHORIZATION, member.bearer()));
    }

    private ResultActions subscribePush(Member member, String endpoint, String p256dh, String auth) throws Exception {
        return mockMvc.perform(post("/api/v1/push/subscriptions").header(HttpHeaders.AUTHORIZATION, member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\": \"%s\", \"p256dh\": \"%s\", \"auth\": \"%s\"}".formatted(endpoint, p256dh, auth)));
    }

    private Long createAuction(Member seller, LocalDateTime startAt, LocalDateTime endAt) throws Exception {
        Category parent = categoryRepository.save(new Category(null, "알림" + UUID.randomUUID().toString().substring(0, 6), 1));
        Category leaf = categoryRepository.save(new Category(parent, "카메라", 1));
        Long productId = productRepository.save(new Product(userRepository.findById(seller.id()).orElseThrow(), leaf, "알림 상품", "테스트 상품")).getId();
        String body = "{\"productId\": %d, \"startPrice\": 10000, \"bidUnit\": 1000, \"startAt\": \"%s\", \"endAt\": \"%s\"}"
                .formatted(productId, startAt.withNano(0).format(ISO), endAt.withNano(0).format(ISO));
        return longValue(mockMvc.perform(post("/api/v1/auctions").header(HttpHeaders.AUTHORIZATION, seller.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()), "$.data.id");
    }

    private Long longValue(ResultActions result, String path) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
    }

    private Member signupAndLogin() throws Exception {
        int seq = SEQ.incrementAndGet();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String email = "alert" + seq + "-" + suffix + "@test.com";
        String nickname = "알림" + seq + suffix.substring(0, 4);
        SignupFixture.stubPhoneVerification(verificationService);
        String signup = mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(SignupFixture.signupBody(email, "Abcd1234!", nickname)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(signup, "$.data.userId")).longValue();
        String login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"Abcd1234!\"}"))
                .andReturn().getResponse().getContentAsString();
        return new Member(id, JsonPath.read(login, "$.data.accessToken"));
    }

    private record Member(Long id, String accessToken) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }
}
