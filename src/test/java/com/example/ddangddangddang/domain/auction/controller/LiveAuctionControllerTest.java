package com.example.ddangddangddang.domain.auction.controller;

import com.example.ddangddangddang.domain.auction.dto.response.LiveAuctionChangedMessage;
import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.category.entity.Category;
import com.example.ddangddangddang.domain.category.repository.CategoryRepository;
import com.example.ddangddangddang.domain.livechat.dto.ChatMessageResponse;
import com.example.ddangddangddang.domain.livechat.dto.ChatMessageType;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamUpdatedMessage;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.infra.MediaServerClient;
import com.example.ddangddangddang.domain.livestream.service.LiveStreamService;
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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 방송과 경매: 방송 중 경매를 열거나 이미 만든 경매를 방송에 올리면 방송의 경매 목록에 나오고,
 * 시청 화면에 경매 변경 알림과 채팅 안내가 간다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LiveAuctionControllerTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LiveStreamService liveStreamService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @MockitoBean
    private MediaServerClient mediaServerClient;

    @MockitoBean
    private VerificationService verificationService;

    // 실시간 메시지는 보낸 내용만 확인한다.
    @MockitoBean
    private StompBroadcaster stompBroadcaster;

    @Test
    @DisplayName("미리 만든 경매는 방송 중 경매를 새로 열 수 없지만(AUCTION_003), 방송에 올리면 방송의 경매 목록에 나오고 채팅에 안내된다")
    void showScheduledAuctionOnLive() throws Exception {
        Member seller = signupAndLogin();
        Long productId = createProduct(seller);
        Long auctionId = longValue(createScheduledAuction(seller, productId), "$.data.id");
        Long liveStreamId = startLive(seller);
        clearInvocations(stompBroadcaster);

        mockMvc.perform(post("/api/v1/lives/{liveId}/auctions", liveStreamId).header(HttpHeaders.AUTHORIZATION, seller.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": %d, \"startPrice\": 10000, \"bidUnit\": 1000, \"durationSec\": 60}".formatted(productId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AUCTION_003"));

        mockMvc.perform(post("/api/v1/lives/{liveId}/auctions/{auctionId}", liveStreamId, auctionId).header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liveStreamId").value(liveStreamId))
                .andExpect(jsonPath("$.data.status").value("READY"));

        // 로그인하지 않아도 방송의 경매 목록을 볼 수 있다.
        mockMvc.perform(get("/api/v1/lives/{liveId}/auctions", liveStreamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(auctionId.intValue())));
        mockMvc.perform(get("/api/v1/users/me/auctions").header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(auctionId.intValue())));

        verify(stompBroadcaster).broadcast(eq("/topic/live-streams/%d/auctions".formatted(liveStreamId)), eq(LiveAuctionChangedMessage.of(auctionId)));
        verify(stompBroadcaster).broadcast(eq("/topic/live-streams/%d/chat".formatted(liveStreamId)), argThat(message ->
                message instanceof ChatMessageResponse chat && chat.type() == ChatMessageType.SYSTEM
                        && chat.content().startsWith("‘E2E 카메라’ 경매가 방송에 올라왔습니다.")));

        // 한 번 더 올려도 그대로이고, 알림을 다시 보내지 않는다.
        clearInvocations(stompBroadcaster);
        mockMvc.perform(post("/api/v1/lives/{liveId}/auctions/{auctionId}", liveStreamId, auctionId).header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(status().isOk());
        verify(stompBroadcaster, never()).broadcast(anyString(), argThat(message -> true));

        liveStreamService.end(liveStreamId, seller.id());
    }

    @Test
    @DisplayName("방송 중 경매를 열면 바로 방송의 경매 목록에 나오고, 채팅에 시작 안내가 간다")
    void startLiveAuction() throws Exception {
        Member seller = signupAndLogin();
        Long productId = createProduct(seller);
        Long liveStreamId = startLive(seller);
        clearInvocations(stompBroadcaster);

        Long auctionId = longValue(mockMvc.perform(post("/api/v1/lives/{liveId}/auctions", liveStreamId).header(HttpHeaders.AUTHORIZATION, seller.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": %d, \"startPrice\": 10000, \"bidUnit\": 1000, \"durationSec\": 60}".formatted(productId)))
                .andExpect(status().isCreated()), "$.data.id");

        mockMvc.perform(get("/api/v1/lives/{liveId}/auctions", liveStreamId))
                .andExpect(jsonPath("$.data[0].id").value(auctionId))
                .andExpect(jsonPath("$.data[0].status").value("ONGOING"));
        verify(stompBroadcaster).broadcast(eq("/topic/live-streams/%d/auctions".formatted(liveStreamId)), eq(LiveAuctionChangedMessage.of(auctionId)));
        verify(stompBroadcaster).broadcast(eq("/topic/live-streams/%d/chat".formatted(liveStreamId)), argThat(message ->
                message instanceof ChatMessageResponse chat && chat.content().equals("‘E2E 카메라’ 경매가 시작되었습니다. 시작가 10,000P")));

        liveStreamService.end(liveStreamId, seller.id());
    }

    @Test
    @DisplayName("방송 중 경매는 진행 시간을 3일까지 정할 수 있고, 3일을 넘거나 30초보다 짧으면 400이다")
    void liveAuctionDurationLimit() throws Exception {
        Member seller = signupAndLogin();
        Long liveStreamId = startLive(seller);

        liveAuction(seller, liveStreamId, createProduct(seller), 3 * 24 * 60 * 60 + 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("진행 시간은 3일 이내로 정해 주세요."));
        liveAuction(seller, liveStreamId, createProduct(seller), 29)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("진행 시간은 30초 이상으로 정해 주세요."));

        // 서비스가 요청을 처리하는 순간을 기준으로 마감을 정하므로, 요청 전후 시각 사이 + 3일이어야 한다.
        // (응답 시각은 초 단위로 잘릴 수 있어 요청 전 시각도 초 단위로 맞춘다)
        LocalDateTime before = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        String created = liveAuction(seller, liveStreamId, createProduct(seller), 3 * 24 * 60 * 60)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        LocalDateTime after = LocalDateTime.now();
        LocalDateTime endAt = LocalDateTime.parse(JsonPath.read(created, "$.data.endAt"));
        assertThat(endAt).isBetween(before.plusDays(3), after.plusDays(3));

        liveStreamService.end(liveStreamId, seller.id());
    }

    private ResultActions liveAuction(Member seller, Long liveStreamId, Long productId, int durationSec) throws Exception {
        return mockMvc.perform(post("/api/v1/lives/{liveId}/auctions", liveStreamId).header(HttpHeaders.AUTHORIZATION, seller.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\": %d, \"startPrice\": 10000, \"bidUnit\": 1000, \"durationSec\": %d}".formatted(productId, durationSec)));
    }

    @Test
    @DisplayName("방송을 시작하면 방송 예정 화면의 시청자에게 LIVE가 된 방송 정보와 채팅 시작 안내가 간다")
    void startNotifiesViewers() throws Exception {
        Member seller = signupAndLogin();
        Long liveStreamId = startLive(seller);

        verify(stompBroadcaster).broadcast(eq("/topic/live-streams/%d".formatted(liveStreamId)), argThat(message ->
                message instanceof LiveStreamUpdatedMessage updated && updated.liveStream().status() == LiveStreamStatus.LIVE));
        verify(stompBroadcaster).broadcast(eq("/topic/live-streams/%d/chat".formatted(liveStreamId)), argThat(message ->
                message instanceof ChatMessageResponse chat && chat.type() == ChatMessageType.SYSTEM && chat.content().equals("방송이 시작되었습니다.")));

        liveStreamService.end(liveStreamId, seller.id());
    }

    @Test
    @DisplayName("방송에서 연 경매는 방송 중에만 방송 중 경매로 나오고, 방송이 끝나면 경매가 이어져도 방송 중 경매가 아니다")
    void liveFlagFollowsLiveStream() throws Exception {
        Member seller = signupAndLogin();
        Long productId = createProduct(seller);
        Long liveStreamId = startLive(seller);
        Long auctionId = longValue(mockMvc.perform(post("/api/v1/lives/{liveId}/auctions", liveStreamId).header(HttpHeaders.AUTHORIZATION, seller.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": %d, \"startPrice\": 10000, \"bidUnit\": 1000, \"durationSec\": 600}".formatted(productId)))
                .andExpect(status().isCreated()), "$.data.id");
        String inList = "$.data.content[?(@.auctionId == %d)].live".formatted(auctionId);

        mockMvc.perform(get("/api/v1/auctions/{auctionId}", auctionId)).andExpect(jsonPath("$.data.live").value(true));
        mockMvc.perform(get("/api/v1/auctions").param("productName", "E2E 카메라").param("size", "50"))
                .andExpect(jsonPath(inList).value(hasItem(true)));

        liveStreamService.end(liveStreamId, seller.id());

        mockMvc.perform(get("/api/v1/auctions/{auctionId}", auctionId))
                .andExpect(jsonPath("$.data.status").value("ONGOING"))
                .andExpect(jsonPath("$.data.liveStreamId").value(liveStreamId))
                .andExpect(jsonPath("$.data.live").value(false));
        mockMvc.perform(get("/api/v1/auctions").param("productName", "E2E 카메라").param("size", "50"))
                .andExpect(jsonPath(inList).value(hasItem(false)));
    }

    @Test
    @DisplayName("다른 사람의 경매는 내 방송에 올릴 수 없고(AUCTION_006), 내 경매를 남의 방송에 올릴 수 없고(LIVESTREAM_002), 방송 중이 아니면 올릴 수 없다")
    void cannotShowOthersAuction() throws Exception {
        Member owner = signupAndLogin();
        Long auctionId = longValue(createScheduledAuction(owner, createProduct(owner)), "$.data.id");
        Member other = signupAndLogin();
        Long otherLive = startLive(other);

        mockMvc.perform(post("/api/v1/lives/{liveId}/auctions/{auctionId}", otherLive, auctionId).header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUCTION_006"));
        mockMvc.perform(post("/api/v1/lives/{liveId}/auctions/{auctionId}", otherLive, auctionId).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("LIVESTREAM_002"));

        liveStreamService.end(otherLive, other.id());
        mockMvc.perform(post("/api/v1/lives/{liveId}/auctions/{auctionId}", otherLive, auctionId).header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.error.code").value(org.hamcrest.Matchers.startsWith("LIVESTREAM_")));
    }

    @Test
    @DisplayName("취소된 경매는 방송에 올릴 수 없고(AUCTION_010), 이미 이 방송에 올린 경매는 그 뒤 취소돼도 다시 요청하면 그대로 돌려준다")
    void canceledAuction() throws Exception {
        Member seller = signupAndLogin();
        Long liveStreamId = startLive(seller);
        Long shown = longValue(createScheduledAuction(seller, createProduct(seller)), "$.data.id");
        mockMvc.perform(post("/api/v1/lives/{liveId}/auctions/{auctionId}", liveStreamId, shown).header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(status().isOk());
        cancel(seller, shown);
        mockMvc.perform(post("/api/v1/lives/{liveId}/auctions/{auctionId}", liveStreamId, shown).header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));

        Long canceled = longValue(createScheduledAuction(seller, createProduct(seller)), "$.data.id");
        cancel(seller, canceled);
        mockMvc.perform(post("/api/v1/lives/{liveId}/auctions/{auctionId}", liveStreamId, canceled).header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AUCTION_010"));

        liveStreamService.end(liveStreamId, seller.id());
    }

    private void cancel(Member seller, Long auctionId) throws Exception {
        mockMvc.perform(post("/api/v1/auctions/{auctionId}/cancel", auctionId).header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(status().isNoContent());
    }

    private Long createProduct(Member seller) {
        Category parent = categoryRepository.save(new Category(null, "카메라" + UUID.randomUUID().toString().substring(0, 6), 1));
        Category leaf = categoryRepository.save(new Category(parent, "필름 카메라", 1));
        Product product = productRepository.save(new Product(userRepository.findById(seller.id()).orElseThrow(), leaf, "E2E 카메라", "테스트 상품"));
        return product.getId();
    }

    private ResultActions createScheduledAuction(Member seller, Long productId) throws Exception {
        LocalDateTime startAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS);
        return mockMvc.perform(post("/api/v1/auctions").header(HttpHeaders.AUTHORIZATION, seller.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": %d, \"startPrice\": 10000, \"bidUnit\": 1000, \"startAt\": \"%s\", \"endAt\": \"%s\"}"
                                .formatted(productId, startAt, startAt.plusHours(1))))
                .andExpect(status().isCreated());
    }

    private Long startLive(Member seller) throws Exception {
        Long liveStreamId = longValue(mockMvc.perform(post("/api/v1/live-streams").header(HttpHeaders.AUTHORIZATION, seller.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"경매 방송\"}"))
                .andExpect(status().isCreated()), "$.data.id");
        given(mediaServerClient.createPublisherToken(anyString(), anyString(), anyString())).willReturn("token");
        liveStreamService.start(liveStreamId, seller.id(), seller.nickname());
        return liveStreamId;
    }

    private Long longValue(ResultActions result, String path) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
    }

    private Member signupAndLogin() throws Exception {
        int seq = SEQ.incrementAndGet();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String email = "la" + seq + "-" + suffix + "@test.com";
        String nickname = "경매" + seq + suffix.substring(0, 4);
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
