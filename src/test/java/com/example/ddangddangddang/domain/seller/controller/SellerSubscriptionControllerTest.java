package com.example.ddangddangddang.domain.seller.controller;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.category.entity.Category;
import com.example.ddangddangddang.domain.category.repository.CategoryRepository;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.infra.MediaServerClient;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.domain.product.repository.ProductRepository;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.domain.livestream.service.LiveStreamService;
import com.example.ddangddangddang.domain.notification.dto.NotificationResponse;
import com.example.ddangddangddang.domain.notification.entity.NotificationType;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.entity.UserRole;
import com.example.ddangddangddang.global.security.jwt.JwtProvider;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
import com.example.ddangddangddang.domain.seller.repository.SellerApplicationRepository;
import com.example.ddangddangddang.support.SellerFixture;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 판매자 구독·방송 시작 알림·판매자 신청과 인증·판매자 프로필
 */
@SpringBootTest
@AutoConfigureMockMvc
class SellerSubscriptionControllerTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SellerApplicationRepository sellerApplicationRepository;

    @Autowired
    private LiveStreamService liveStreamService;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LiveStreamRepository liveStreamRepository;

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
    @DisplayName("구독한 판매자가 방송을 시작하면 알림함에 쌓이고 실시간으로도 오며, 구독 목록에 LIVE로 나온다")
    void subscribeAndNotifyOnLiveStart() throws Exception {
        Member seller = signupAndLogin();
        Member viewer = signupAndLogin();

        // 구독: 두 번 눌러도 한 번만 센다.
        subscribe(viewer, seller.id()).andExpect(jsonPath("$.data.subscribed").value(true))
                .andExpect(jsonPath("$.data.subscriberCount").value(1));
        subscribe(viewer, seller.id()).andExpect(jsonPath("$.data.subscriberCount").value(1));
        mockMvc.perform(get("/api/v1/users/me/subscriptions").header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
                .andExpect(jsonPath("$.data[0].sellerId").value(seller.id()))
                .andExpect(jsonPath("$.data[0].nickname").value(seller.nickname()))
                .andExpect(jsonPath("$.data[0].liveStreamId").doesNotExist());

        Long liveStreamId = startLive(seller, "구독 알림 방송");

        verify(stompBroadcaster, timeout(5000)).sendToUser(eq(viewer.id()), eq("/queue/notifications"), argThat(message ->
                message instanceof NotificationResponse n && n.type() == NotificationType.LIVE_STARTED
                        && n.liveStreamId().equals(liveStreamId) && n.sellerNickname().equals(seller.nickname())));
        mockMvc.perform(get("/api/v1/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
                .andExpect(jsonPath("$.data.count").value(1));
        Long notificationId = longValue(mockMvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
                .andExpect(jsonPath("$.data.content[0].liveTitle").value("구독 알림 방송"))
                .andExpect(jsonPath("$.data.content[0].read").value(false)), "$.data.content[0].id");
        mockMvc.perform(get("/api/v1/users/me/subscriptions").header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
                .andExpect(jsonPath("$.data[0].liveStreamId").value(liveStreamId))
                .andExpect(jsonPath("$.data[0].liveTitle").value("구독 알림 방송"));

        // 다른 사람의 알림은 읽음으로 바꿀 수 없다.
        mockMvc.perform(post("/api/v1/notifications/{id}/read", notificationId).header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_001"));
        mockMvc.perform(post("/api/v1/notifications/{id}/read", notificationId).header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
                .andExpect(jsonPath("$.data.read").value(true));
        mockMvc.perform(get("/api/v1/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
                .andExpect(jsonPath("$.data.count").value(0));

        liveStreamService.end(liveStreamId, seller.id());

        // 구독 취소하면 구독자 수가 줄고 목록에서 빠진다.
        mockMvc.perform(delete("/api/v1/sellers/{id}/subscription", seller.id()).header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
                .andExpect(jsonPath("$.data.subscribed").value(false))
                .andExpect(jsonPath("$.data.subscriberCount").value(0));
        mockMvc.perform(get("/api/v1/users/me/subscriptions").header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("자기 자신은 구독할 수 없고(SELLER_005), 없는 회원은 USER_003, 로그인하지 않으면 401")
    void subscribeErrors() throws Exception {
        Member member = signupAndLogin();

        subscribe(member, member.id()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("SELLER_005"));
        subscribe(member, 999_999_999L).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("USER_003"));
        mockMvc.perform(put("/api/v1/sellers/{id}/subscription", member.id())).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("판매자 신청 → 관리자 승인이면 프로필에 인증과 상점 정보가 나온다. 반려되면 사유를 보고 다시 신청한다")
    void applyAndReview() throws Exception {
        Member seller = signupAndLogin();

        Long applicationId = longValue(apply(seller, "필름 상점", "빈티지 필름 카메라를 팝니다.")
                .andExpect(jsonPath("$.data.status").value("PENDING")), "$.data.id");
        // 심사 중에는 다시 신청할 수 없다.
        apply(seller, "필름 상점", "다시").andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SELLER_001"));
        // 일반 회원은 심사할 수 없다.
        mockMvc.perform(post("/api/v1/admin/seller-applications/{id}/approve", applicationId).header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(status().isForbidden());

        // 반려 → 사유 확인 → 다시 신청 → 승인
        mockMvc.perform(post("/api/v1/admin/seller-applications/{id}/reject", applicationId).header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"소개를 자세히 적어 주세요.\"}"))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
        mockMvc.perform(get("/api/v1/seller-applications/me").header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(jsonPath("$.data.rejectReason").value("소개를 자세히 적어 주세요."));
        apply(seller, "필름 상점", "1970년대 필름 카메라를 점검해서 팝니다.").andExpect(jsonPath("$.data.status").value("PENDING"));
        mockMvc.perform(get("/api/v1/admin/seller-applications").param("status", "PENDING").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(jsonPath("$.data.content[0].id").value(applicationId))
                .andExpect(jsonPath("$.data.content[0].nickname").value(seller.nickname()));
        mockMvc.perform(post("/api/v1/admin/seller-applications/{id}/approve", applicationId).header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        apply(seller, "필름 상점", "또").andExpect(jsonPath("$.error.code").value("SELLER_002"));

        // 로그인하지 않아도 프로필을 볼 수 있다.
        mockMvc.perform(get("/api/v1/sellers/{id}/profile", seller.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verifiedSeller").value(true))
                .andExpect(jsonPath("$.data.storeName").value("필름 상점"))
                .andExpect(jsonPath("$.data.introduction").value("1970년대 필름 카메라를 점검해서 팝니다."))
                .andExpect(jsonPath("$.data.subscribed").value(false))
                .andExpect(jsonPath("$.data.trust.phoneVerified").value(true));
    }

    @Test
    @DisplayName("프로필에 첫 방송·방송 수·방송 시간·구독자 수가 나오고, 경매 기록은 커서로 최근 것부터 나온다")
    void profileAndAuctionHistory() throws Exception {
        Member seller = signupAndLogin();
        Member viewer = signupAndLogin();
        subscribe(viewer, seller.id());

        mockMvc.perform(get("/api/v1/sellers/{id}/profile", seller.id()).header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
                .andExpect(jsonPath("$.data.verifiedSeller").value(false))
                .andExpect(jsonPath("$.data.storeName").doesNotExist())
                .andExpect(jsonPath("$.data.firstBroadcastAt").doesNotExist())
                .andExpect(jsonPath("$.data.broadcastCount").value(0))
                .andExpect(jsonPath("$.data.subscriberCount").value(1))
                .andExpect(jsonPath("$.data.subscribed").value(true));

        // 경매가 없으면 빈 목록
        mockMvc.perform(get("/api/v1/sellers/{id}/auctions", seller.id()))
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        Long liveStreamId = startLive(seller, "프로필 방송");
        mockMvc.perform(get("/api/v1/sellers/{id}/profile", seller.id()))
                .andExpect(jsonPath("$.data.liveStreamId").value(liveStreamId))
                .andExpect(jsonPath("$.data.broadcastCount").value(1))
                .andExpect(jsonPath("$.data.firstBroadcastAt").exists());

        // 방송에서 경매 두 개를 열고 하나를 취소하면, 경매 기록에 최근 것부터 1개씩 넘겨 볼 수 있고 취소 수가 신뢰 지표에 잡힌다.
        Long first = startLiveAuction(seller, liveStreamId, createProduct(seller, "프로필 카메라"));
        Long second = startLiveAuction(seller, liveStreamId, createProduct(seller, "프로필 렌즈"));
        mockMvc.perform(post("/api/v1/auctions/{id}/cancel", first).header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(status().isNoContent());
        Long cursor = longValue(mockMvc.perform(get("/api/v1/sellers/{id}/auctions", seller.id()).param("size", "1"))
                .andExpect(jsonPath("$.data.content[0].auctionId").value(second))
                .andExpect(jsonPath("$.data.content[0].productName").value("프로필 렌즈"))
                .andExpect(jsonPath("$.data.content[0].liveStreamId").value(liveStreamId))
                .andExpect(jsonPath("$.data.hasNext").value(true)), "$.data.nextCursor");
        mockMvc.perform(get("/api/v1/sellers/{id}/auctions", seller.id()).param("size", "1").param("cursor", String.valueOf(cursor)))
                .andExpect(jsonPath("$.data.content[0].auctionId").value(first))
                .andExpect(jsonPath("$.data.content[0].status").value("CANCELED"))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        // 끝난 방송의 시간은 집계 쿼리로 더한다. 1시간 방송한 것으로 시작 시각을 앞당긴다.
        liveStreamService.end(liveStreamId, seller.id());
        LiveStream ended = liveStreamRepository.findById(liveStreamId).orElseThrow();
        ReflectionTestUtils.setField(ended, "startedAt", ended.getEndedAt().minusHours(1));
        liveStreamRepository.saveAndFlush(ended);
        mockMvc.perform(get("/api/v1/sellers/{id}/profile", seller.id()))
                .andExpect(jsonPath("$.data.liveStreamId").doesNotExist())
                .andExpect(jsonPath("$.data.broadcastCount").value(1))
                .andExpect(jsonPath("$.data.totalBroadcastSeconds").value(3600))
                .andExpect(jsonPath("$.data.trust.canceledAuctionCount").value(1))
                .andExpect(jsonPath("$.data.trust.soldAuctionCount").value(0))
                .andExpect(jsonPath("$.data.trust.averageShippingHours").doesNotExist());

        // 없는 회원은 USER_003
        mockMvc.perform(get("/api/v1/sellers/{id}/auctions", 999_999_999L))
                .andExpect(jsonPath("$.error.code").value("USER_003"));
    }

    private ResultActions subscribe(Member member, Long sellerId) throws Exception {
        return mockMvc.perform(put("/api/v1/sellers/{id}/subscription", sellerId).header(HttpHeaders.AUTHORIZATION, member.bearer()));
    }

    private ResultActions apply(Member member, String storeName, String introduction) throws Exception {
        return mockMvc.perform(post("/api/v1/seller-applications").header(HttpHeaders.AUTHORIZATION, member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"storeName\": \"%s\", \"introduction\": \"%s\"}".formatted(storeName, introduction)));
    }

    private Long createProduct(Member seller, String name) {
        Category parent = categoryRepository.save(new Category(null, "구독" + UUID.randomUUID().toString().substring(0, 6), 1));
        Category leaf = categoryRepository.save(new Category(parent, "카메라", 1));
        return productRepository.save(new Product(userRepository.findById(seller.id()).orElseThrow(), leaf, name, "테스트 상품")).getId();
    }

    private Long startLiveAuction(Member seller, Long liveStreamId, Long productId) throws Exception {
        return longValue(mockMvc.perform(post("/api/v1/lives/{liveId}/auctions", liveStreamId).header(HttpHeaders.AUTHORIZATION, seller.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": %d, \"startPrice\": 10000, \"bidUnit\": 1000, \"durationSec\": 60}".formatted(productId)))
                .andExpect(status().isCreated()), "$.data.id");
    }

    private Long startLive(Member seller, String title) throws Exception {
        Long liveStreamId = longValue(mockMvc.perform(post("/api/v1/live-streams").header(HttpHeaders.AUTHORIZATION, seller.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"%s\"}".formatted(title)))
                .andExpect(status().isCreated()), "$.data.id");
        given(mediaServerClient.createPublisherToken(anyString(), anyString(), anyString())).willReturn("token");
        SellerFixture.verify(sellerApplicationRepository, seller.id());

        liveStreamService.start(liveStreamId, seller.id(), seller.nickname());
        return liveStreamId;
    }

    // Access Token의 권한 클레임만으로 인증하므로, 관리자 권한을 가진 토큰을 직접 만든다.
    private String adminBearer() {
        User admin = User.create("seller-admin@test.com", "encoded", "관리자");
        ReflectionTestUtils.setField(admin, "id", 900_001L);
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);
        return "Bearer " + jwtProvider.createAccessToken(admin);
    }

    private Long longValue(ResultActions result, String path) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
    }

    private Member signupAndLogin() throws Exception {
        int seq = SEQ.incrementAndGet();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String email = "sub" + seq + "-" + suffix + "@test.com";
        String nickname = "구독" + seq + suffix.substring(0, 4);
        SignupFixture.stubPhoneVerification(verificationService);
        String signup = mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(SignupFixture.signupBody(email, "Abcd1234!", nickname)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(signup, "$.data.userId")).longValue();
        String login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"Abcd1234!\"}"))
                .andReturn().getResponse().getContentAsString();
        return new Member(id, nickname, JsonPath.read(login, "$.data.accessToken"));
    }

    private record Member(Long id, String nickname, String accessToken) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }
}
