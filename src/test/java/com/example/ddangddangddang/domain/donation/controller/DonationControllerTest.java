package com.example.ddangddangddang.domain.donation.controller;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.donation.entity.DonationPointLot;
import com.example.ddangddangddang.domain.donation.repository.DonationPointLotRepository;
import com.example.ddangddangddang.domain.donation.service.DonationPointExpirer;
import com.example.ddangddangddang.domain.donation.service.DonationPointService;
import com.example.ddangddangddang.domain.donation.service.DonationWalletLocker;
import com.example.ddangddangddang.domain.livechat.dto.ChatMessageResponse;
import com.example.ddangddangddang.domain.livechat.dto.ChatMessageType;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.infra.MediaServerClient;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.notification.dto.NotificationResponse;
import com.example.ddangddangddang.domain.notification.entity.NotificationType;
import com.example.ddangddangddang.domain.order.event.OrderConfirmedEvent;
import com.example.ddangddangddang.domain.seller.repository.SellerApplicationRepository;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
import com.example.ddangddangddang.support.SellerFixture;
import com.example.ddangddangddang.support.SignupFixture;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 도네이션 포인트: 구매 확정 적립 → 방송 중인 판매자에게 선물 → 유효기간 만료 → 판매자 상점(파워 판매자) → 신뢰 지표
 * 기본 설정(DonationProperties): 낙찰가의 1%(주문당 최대 1,000P), 한 달 적립 10,000P, 유효기간 90일,
 * 선물 100~5,000P(100P 단위), 판매자 한 명에게 한 달 5,000P, 파워 판매자 30,000P/30일
 */
@SpringBootTest
@AutoConfigureMockMvc
class DonationControllerTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    // 적립 묶음의 order_id는 유니크라 테스트마다 다른 주문 번호를 쓴다. (주문 테이블과 연관관계는 없음)
    private static final AtomicLong ORDER_SEQ = new AtomicLong(System.nanoTime() % 1_000_000_000L);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DonationPointService donationPointService;

    @Autowired
    private DonationPointExpirer expirer;

    @Autowired
    private DonationPointLotRepository lotRepository;

    @Autowired
    private DonationWalletLocker walletLocker;

    @Autowired
    private LiveStreamRepository liveStreamRepository;

    @Autowired
    private SellerApplicationRepository sellerApplicationRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

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
    @DisplayName("구매 확정하면 낙찰가의 1%(주문당 최대 1,000P)를 적립하고 알린다. 같은 주문은 한 번만, 한 달 10,000P까지 적립한다")
    void earn() throws Exception {
        Member buyer = signupAndLogin();
        Member seller = signupAndLogin();

        // 구매 확정이 커밋되면 따로 된 스레드에서 적립한다.
        long orderId = ORDER_SEQ.incrementAndGet();
        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(new OrderConfirmedEvent(orderId, buyer.id(), seller.id(), 35_000L)));
        verify(stompBroadcaster, timeout(5000)).sendToUser(eq(buyer.id()), eq("/queue/notifications"), argThat(message ->
                message instanceof NotificationResponse n && n.type() == NotificationType.DONATION_POINT_EARNED
                        && n.title().equals("도네이션 포인트 350P가 적립됐어요") && n.link().equals("/lives")));
        wallet(buyer).andExpect(jsonPath("$.data.giftableBalance").value(350))
                .andExpect(jsonPath("$.data.earnedThisMonth").value(350))
                .andExpect(jsonPath("$.data.monthlyEarnLimit").value(10_000))
                .andExpect(jsonPath("$.data.nextExpiresAt").exists());

        // 같은 주문은 다시 적립하지 않는다. 비싼 물건은 주문당 1,000P까지.
        assertThat(donationPointService.earn(orderId, buyer.id(), seller.id(), 35_000L)).isZero();
        assertThat(donationPointService.earn(ORDER_SEQ.incrementAndGet(), buyer.id(), seller.id(), 5_000_000L)).isEqualTo(1_000);

        // 한 달 한도(10,000P)를 넘는 만큼은 적립하지 않는다. (350 + 1,000 + 1,000 × 8 = 9,350 → 다음은 650만)
        for (int i = 0; i < 8; i++) {
            donationPointService.earn(ORDER_SEQ.incrementAndGet(), buyer.id(), seller.id(), 5_000_000L);
        }
        assertThat(donationPointService.earn(ORDER_SEQ.incrementAndGet(), buyer.id(), seller.id(), 5_000_000L)).isEqualTo(650);
        assertThat(donationPointService.earn(ORDER_SEQ.incrementAndGet(), buyer.id(), seller.id(), 5_000_000L)).isZero();
        wallet(buyer).andExpect(jsonPath("$.data.giftableBalance").value(10_000))
                .andExpect(jsonPath("$.data.earnedThisMonth").value(10_000));
        mockMvc.perform(get("/api/v1/donation-points/histories").header(HttpHeaders.AUTHORIZATION, buyer.bearer()))
                .andExpect(jsonPath("$.data.content[0].type").value("EARN"))
                .andExpect(jsonPath("$.data.content[0].giftableAfter").value(10_000));
    }

    @Test
    @DisplayName("방송 중인 판매자에게 선물하면 내 포인트가 판매자에게 가고, 방송 채팅으로 선물 이펙트가 나간다. 같은 요청 키는 한 번만 선물한다")
    void gift() throws Exception {
        Member viewer = signupAndLogin();
        Member seller = signupAndLogin();
        Member otherSeller = signupAndLogin();
        Long liveStreamId = liveStream(seller, true);
        // 다른 판매자와의 거래로 1,000P를 받았다.
        donationPointService.earn(ORDER_SEQ.incrementAndGet(), viewer.id(), otherSeller.id(), 100_000L);

        mockMvc.perform(get("/api/v1/live-streams/{id}/donations/me", liveStreamId).header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
                .andExpect(jsonPath("$.data.sellerId").value(seller.id()))
                .andExpect(jsonPath("$.data.usableBalance").value(1_000))
                .andExpect(jsonPath("$.data.remainingThisMonth").value(5_000))
                .andExpect(jsonPath("$.data.minGift").value(100));

        String key = UUID.randomUUID().toString();
        String first = gift(viewer, liveStreamId, 300, "오늘 방송 최고예요", key)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(300))
                .andExpect(jsonPath("$.data.giftableBalance").value(700))
                .andExpect(jsonPath("$.data.remainingThisMonth").value(4_700))
                .andReturn().getResponse().getContentAsString();
        verify(stompBroadcaster, timeout(5000)).broadcast(eq("/topic/live-streams/" + liveStreamId + "/chat"), argThat(message ->
                message instanceof ChatMessageResponse m && m.type() == ChatMessageType.DONATION
                        && m.senderId().equals(viewer.id()) && m.amount() == 300L && "오늘 방송 최고예요".equals(m.content())));

        // 연타·재전송: 같은 요청 키면 처음 선물을 그대로 돌려주고 다시 빼지 않는다.
        gift(viewer, liveStreamId, 300, "오늘 방송 최고예요", key)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.donationId").value(((Number) JsonPath.read(first, "$.data.donationId")).longValue()))
                .andExpect(jsonPath("$.data.giftableBalance").value(700));
        wallet(viewer).andExpect(jsonPath("$.data.giftableBalance").value(700));
        wallet(seller).andExpect(jsonPath("$.data.receivedBalance").value(300));
        mockMvc.perform(get("/api/v1/donation-points/histories").header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(jsonPath("$.data.content[0].type").value("RECEIVE"))
                .andExpect(jsonPath("$.data.content[0].amount").value(300));

        // 판매자 프로필: 응원한 사람 수·받은 포인트
        mockMvc.perform(get("/api/v1/sellers/{id}/profile", seller.id()))
                .andExpect(jsonPath("$.data.trust.donorCount").value(1))
                .andExpect(jsonPath("$.data.trust.receivedDonationPoints").value(300))
                .andExpect(jsonPath("$.data.trust.donationWindowDays").value(90))
                .andExpect(jsonPath("$.data.powerSeller").value(false));
    }

    @Test
    @DisplayName("방송 중이 아니거나, 내 방송이거나, 금액·메시지가 맞지 않거나, 포인트가 부족하면 선물할 수 없다")
    void giftRules() throws Exception {
        Member viewer = signupAndLogin();
        Member seller = signupAndLogin();
        Member otherSeller = signupAndLogin();
        Long scheduled = liveStream(seller, false);
        donationPointService.earn(ORDER_SEQ.incrementAndGet(), viewer.id(), otherSeller.id(), 100_000L);

        gift(viewer, scheduled, 100, null, UUID.randomUUID().toString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("DONATION_004"));
        Long live = startLive(scheduled, seller);
        gift(seller, live, 100, null, UUID.randomUUID().toString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("DONATION_005"));
        // 최소 100P, 100P 단위, 최대 5,000P
        for (long amount : new long[]{50, 150, 5_100}) {
            gift(viewer, live, amount, null, UUID.randomUUID().toString())
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("DONATION_001"));
        }
        // 선물 메시지에도 연락처는 넣을 수 없다.
        gift(viewer, live, 100, "연락주세요 010-1234-5678", UUID.randomUUID().toString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("DONATION_007"));
        // 1,000P밖에 없다.
        gift(viewer, live, 2_000, null, UUID.randomUUID().toString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("DONATION_002"));
        wallet(viewer).andExpect(jsonPath("$.data.giftableBalance").value(1_000));
    }

    @Test
    @DisplayName("판매자 한 명에게는 한 달 5,000P까지 선물할 수 있고, 그 판매자와의 거래로 받은 포인트는 그 판매자에게 쓸 수 없다")
    void limits() throws Exception {
        Member viewer = signupAndLogin();
        Member seller = signupAndLogin();
        Member otherSeller = signupAndLogin();
        Long live = liveStream(seller, true);
        // 이 판매자와의 거래로 1,000P (이 판매자에게는 못 씀)
        donationPointService.earn(ORDER_SEQ.incrementAndGet(), viewer.id(), seller.id(), 100_000L);
        gift(viewer, live, 500, null, UUID.randomUUID().toString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("DONATION_006"));

        // 다른 판매자와의 거래로 6,000P
        for (int i = 0; i < 6; i++) {
            donationPointService.earn(ORDER_SEQ.incrementAndGet(), viewer.id(), otherSeller.id(), 100_000L);
        }
        gift(viewer, live, 5_000, null, UUID.randomUUID().toString()).andExpect(status().isCreated());
        gift(viewer, live, 100, null, UUID.randomUUID().toString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("DONATION_003"))
                .andExpect(jsonPath("$.error.message").value("이 판매자에게 이번 달 선물할 수 있는 포인트는 0P 남았습니다."));
        // 남은 2,000P(이 판매자 거래분 1,000P 포함)는 다른 판매자에게 선물할 수 있다.
        mockMvc.perform(get("/api/v1/live-streams/{id}/donations/me", live).header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
                .andExpect(jsonPath("$.data.giftableBalance").value(2_000))
                .andExpect(jsonPath("$.data.usableBalance").value(1_000))
                .andExpect(jsonPath("$.data.remainingThisMonth").value(0));
    }

    @Test
    @DisplayName("유효기간이 지난 포인트는 새벽 정리에서 사라지고, 곧 사라질 포인트는 한 번 알린다")
    void expiry() throws Exception {
        Member viewer = signupAndLogin();
        Member seller = signupAndLogin();
        // 이미 유효기간이 지난 400P, 3일 뒤 사라지는 600P
        lot(viewer, seller, 400, LocalDateTime.now().minusDays(1));
        lot(viewer, seller, 600, LocalDateTime.now().plusDays(3));
        wallet(viewer).andExpect(jsonPath("$.data.giftableBalance").value(1_000))
                .andExpect(jsonPath("$.data.expiringSoon").value(600));

        assertThat(expirer.expire()).isGreaterThanOrEqualTo(400);
        wallet(viewer).andExpect(jsonPath("$.data.giftableBalance").value(600));
        mockMvc.perform(get("/api/v1/donation-points/histories").header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
                .andExpect(jsonPath("$.data.content[0].type").value("EXPIRE"))
                .andExpect(jsonPath("$.data.content[0].amount").value(400));

        expirer.notifyExpiring();
        expirer.notifyExpiring();
        verify(stompBroadcaster, after(500).times(1)).sendToUser(eq(viewer.id()), eq("/queue/notifications"), argThat(message ->
                message instanceof NotificationResponse n && n.type() == NotificationType.DONATION_POINT_EXPIRING
                        && n.title().equals("도네이션 포인트 600P가 곧 사라져요")));
    }

    @Test
    @DisplayName("인증 판매자는 받은 포인트로 파워 판매자를 사고(다시 사면 기간이 이어짐), 프로필에 배지가 붙고 방송이 라이브 목록 맨 위에 올라간다")
    void powerSeller() throws Exception {
        Member seller = signupAndLogin();
        Member fan = signupAndLogin();
        mockMvc.perform(get("/api/v1/seller-perks/shop").header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(jsonPath("$.data[0].type").value("POWER_SELLER"))
                .andExpect(jsonPath("$.data[0].price").value(30_000))
                .andExpect(jsonPath("$.data[0].periodDays").value(30));

        // 인증 전에는 살 수 없다.
        buyPower(seller).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("DONATION_009"));
        SellerFixture.verify(sellerApplicationRepository, seller.id());
        buyPower(seller).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("DONATION_008"));

        // 받은 포인트 60,000P (선물받은 것처럼 지갑에 바로 넣는다)
        walletLocker.prepare(seller.id());
        transactionTemplate.executeWithoutResult(status -> walletLocker.lock(seller.id()).receive(60_000));
        String first = buyPower(seller).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = buyPower(seller).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        // 두 번째는 첫 번째가 끝나는 시각부터 30일
        assertThat((String) JsonPath.read(second, "$.data.startsAt")).isEqualTo(JsonPath.read(first, "$.data.endsAt"));
        wallet(seller).andExpect(jsonPath("$.data.receivedBalance").value(0));
        mockMvc.perform(get("/api/v1/seller-perks/me").header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get("/api/v1/sellers/{id}/profile", seller.id())).andExpect(jsonPath("$.data.powerSeller").value(true));

        // 늦게 시작했어도 파워 판매자 방송이 맨 위
        Long power = liveStream(seller, true);
        liveStream(fan, true);
        mockMvc.perform(get("/api/v1/live-streams"))
                .andExpect(jsonPath("$.data.content[0].liveStreamId").value(power))
                .andExpect(jsonPath("$.data.content[0].powerSeller").value(true));
    }

    private ResultActions gift(Member giver, Long liveStreamId, long amount, String message, String key) throws Exception {
        String body = message == null
                ? "{\"amount\": %d, \"requestKey\": \"%s\"}".formatted(amount, key)
                : "{\"amount\": %d, \"message\": \"%s\", \"requestKey\": \"%s\"}".formatted(amount, message, key);
        return mockMvc.perform(post("/api/v1/live-streams/{id}/donations", liveStreamId)
                .header(HttpHeaders.AUTHORIZATION, giver.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions buyPower(Member seller) throws Exception {
        return mockMvc.perform(post("/api/v1/seller-perks").header(HttpHeaders.AUTHORIZATION, seller.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\": \"POWER_SELLER\"}"));
    }

    private ResultActions wallet(Member member) throws Exception {
        return mockMvc.perform(get("/api/v1/donation-points/wallet").header(HttpHeaders.AUTHORIZATION, member.bearer()))
                .andExpect(status().isOk());
    }

    /** 유효기간을 정해 적립 묶음을 바로 넣는다. (지갑 잔액도 함께 올린다) */
    private void lot(Member owner, Member seller, long amount, LocalDateTime expiresAt) {
        walletLocker.prepare(owner.id());
        transactionTemplate.executeWithoutResult(status -> {
            walletLocker.lock(owner.id()).earn(amount);
            lotRepository.save(DonationPointLot.earn(owner.id(), ORDER_SEQ.incrementAndGet(), seller.id(), amount,
                    expiresAt.minusDays(90), expiresAt));
        });
    }

    /** 판매자의 방송. live면 방송 중으로 만든다. (송출 서버는 쓰지 않는다) */
    private Long liveStream(Member seller, boolean live) {
        liveStreamRepository.findByActiveSellerId(seller.id()).ifPresent(active -> {
            active.end(seller.id());
            liveStreamRepository.save(active);
        });
        LiveStream stream = LiveStream.create(seller.id(), "응원 방송 " + SEQ.incrementAndGet());
        if (live) {
            stream.start(seller.id());
        }
        return liveStreamRepository.save(stream).getId();
    }

    private Long startLive(Long liveStreamId, Member seller) {
        LiveStream stream = liveStreamRepository.findById(liveStreamId).orElseThrow();
        stream.start(seller.id());
        return liveStreamRepository.save(stream).getId();
    }

    private Member signupAndLogin() throws Exception {
        int seq = SEQ.incrementAndGet();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String email = "donation" + seq + "-" + suffix + "@test.com";
        String nickname = "응원" + seq + suffix.substring(0, 4);
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
