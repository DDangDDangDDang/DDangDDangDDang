package com.example.ddangddangddang.domain.donation.controller;

import com.example.ddangddangddang.domain.auth.repository.LoginAttemptRepository;
import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.donation.entity.DonationLotSource;
import com.example.ddangddangddang.domain.donation.entity.DonationPointLot;
import com.example.ddangddangddang.domain.donation.event.DonationPointEarner;
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
import com.example.ddangddangddang.domain.order.entity.Courier;
import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.event.OrderConfirmedEvent;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.entity.UserRole;
import com.example.ddangddangddang.global.security.jwt.JwtProvider;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 도네이션 마일리지: 구매·판매 확정 적립 → 방송 중인 판매자에게 선물 → 유효기간 만료 → 신뢰 지표 (파워 판매자는 마일리지 상점: ShopControllerTest)
 * 구매·판매 적립과 선물받은 마일리지는 구분 없이 하나의 잔액(balance)으로 쓴다.
 * 기본 설정(DonationProperties): 낙찰가의 1%를 구매자·판매자에게 각각(주문당 최대 1,000P), 한 달 적립 10,000P(구매·판매 각각), 유효기간 90일,
 * 선물 100~5,000P(100P 단위), 판매자 한 명에게 한 달 5,000P
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
    private DonationPointEarner earner;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private LiveStreamRepository liveStreamRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    // 로그인 시도 횟수(Redis). 기본값 0이라 제한에 걸리지 않는다.
    @MockitoBean
    private LoginAttemptRepository loginAttemptRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @MockitoBean
    private MediaServerClient mediaServerClient;

    @MockitoBean
    private VerificationService verificationService;

    @MockitoBean
    private StompBroadcaster stompBroadcaster;

    @Test
    @DisplayName("구매 확정하면 구매자와 판매자에게 낙찰가의 1%(주문당 최대 1,000P)씩 적립하고 알린다. 같은 주문은 한 번만, 한 달 10,000P까지 적립한다")
    void earn() throws Exception {
        Member buyer = signupAndLogin();
        Member seller = signupAndLogin();

        // 구매 확정이 커밋되면 따로 된 스레드에서 적립한다.
        long orderId = ORDER_SEQ.incrementAndGet();
        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(new OrderConfirmedEvent(orderId, buyer.id(), seller.id(), 35_000L)));
        verify(stompBroadcaster, timeout(5000)).sendToUser(eq(buyer.id()), eq("/queue/notifications"), argThat(message ->
                message instanceof NotificationResponse n && n.type() == NotificationType.DONATION_POINT_EARNED
                        && n.title().equals("도네이션 마일리지 350M이 적립됐어요") && n.link().equals("/lives")));
        // 판매자도 같은 주문으로 판매 적립을 받는다.
        verify(stompBroadcaster, timeout(5000)).sendToUser(eq(seller.id()), eq("/queue/notifications"), argThat(message ->
                message instanceof NotificationResponse n && n.type() == NotificationType.DONATION_POINT_EARNED
                        && n.title().equals("도네이션 마일리지 350M이 적립됐어요")));
        wallet(seller).andExpect(jsonPath("$.data.balance").value(350))
                .andExpect(jsonPath("$.data.earnedThisMonth").value(0))
                .andExpect(jsonPath("$.data.saleEarnedThisMonth").value(350));
        wallet(buyer).andExpect(jsonPath("$.data.balance").value(350))
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
        long capped = ORDER_SEQ.incrementAndGet();
        assertThat(donationPointService.earn(capped, buyer.id(), seller.id(), 5_000_000L)).isZero();
        // 한도에 걸린 주문도 처리한 주문으로 남아, 적립 보정이 다음 달에 다시 적립하지 않는다.
        assertThat(lotRepository.existsByOrderIdAndSource(capped, DonationLotSource.PURCHASE)).isTrue();
        wallet(buyer).andExpect(jsonPath("$.data.balance").value(10_000))
                .andExpect(jsonPath("$.data.earnedThisMonth").value(10_000));
        mockMvc.perform(get("/api/v1/donation-points/histories").header(HttpHeaders.AUTHORIZATION, buyer.bearer()))
                .andExpect(jsonPath("$.data.content[0].type").value("EARN"))
                .andExpect(jsonPath("$.data.content[0].balanceAfter").value(10_000));
    }

    @Test
    @DisplayName("판매 적립의 한 달 한도는 구매 적립과 따로 센다. 구매 한도를 다 써도 판매 적립은 받는다")
    void saleEarnLimit() throws Exception {
        Member member = signupAndLogin();
        Member other = signupAndLogin();
        // 구매 적립 10,000P를 다 채운다.
        for (int i = 0; i < 10; i++) {
            donationPointService.earn(ORDER_SEQ.incrementAndGet(), member.id(), other.id(), 5_000_000L);
        }
        assertThat(donationPointService.earn(ORDER_SEQ.incrementAndGet(), member.id(), other.id(), 5_000_000L)).isZero();

        // 판매 적립은 따로 10,000P까지
        long orderId = ORDER_SEQ.incrementAndGet();
        assertThat(donationPointService.earnSale(orderId, member.id(), 5_000_000L)).isEqualTo(1_000);
        assertThat(donationPointService.earnSale(orderId, member.id(), 5_000_000L)).isZero();
        for (int i = 0; i < 9; i++) {
            donationPointService.earnSale(ORDER_SEQ.incrementAndGet(), member.id(), 5_000_000L);
        }
        assertThat(donationPointService.earnSale(ORDER_SEQ.incrementAndGet(), member.id(), 5_000_000L)).isZero();

        wallet(member).andExpect(jsonPath("$.data.balance").value(20_000))
                .andExpect(jsonPath("$.data.earnedThisMonth").value(10_000))
                .andExpect(jsonPath("$.data.saleEarnedThisMonth").value(10_000));
        mockMvc.perform(get("/api/v1/donation-points/histories").header(HttpHeaders.AUTHORIZATION, member.bearer()))
                .andExpect(jsonPath("$.data.content[0].type").value("SALE"));
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
                .andExpect(jsonPath("$.data.balance").value(1_000))
                .andExpect(jsonPath("$.data.remainingThisMonth").value(5_000))
                .andExpect(jsonPath("$.data.minGift").value(100));

        String key = UUID.randomUUID().toString();
        String first = gift(viewer, liveStreamId, 300, "오늘 방송 최고예요", key)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(300))
                .andExpect(jsonPath("$.data.balance").value(700))
                .andExpect(jsonPath("$.data.remainingThisMonth").value(4_700))
                .andReturn().getResponse().getContentAsString();
        verify(stompBroadcaster, timeout(5000)).broadcast(eq("/topic/live-streams/" + liveStreamId + "/chat"), argThat(message ->
                message instanceof ChatMessageResponse m && m.type() == ChatMessageType.DONATION
                        && m.senderId().equals(viewer.id()) && m.amount() == 300L && "오늘 방송 최고예요".equals(m.content())));

        // 연타·재전송: 같은 요청 키면 처음 선물을 그대로 돌려주고 다시 빼지 않는다.
        gift(viewer, liveStreamId, 300, "오늘 방송 최고예요", key)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.donationId").value(((Number) JsonPath.read(first, "$.data.donationId")).longValue()))
                .andExpect(jsonPath("$.data.balance").value(700));
        wallet(viewer).andExpect(jsonPath("$.data.balance").value(700));
        // 같은 키로 다른 선물(금액이 다름)을 보내면 처음 결과를 돌려주지 않고 거부한다.
        gift(viewer, liveStreamId, 500, null, key)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("DONATION_010"));
        wallet(viewer).andExpect(jsonPath("$.data.balance").value(700));
        // 받은 마일리지도 같은 잔액으로 쌓이고, 받은 날부터 90일 유효하다.
        wallet(seller).andExpect(jsonPath("$.data.balance").value(300))
                .andExpect(jsonPath("$.data.nextExpiresAt").exists());
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
        // 선물 메시지는 20자까지
        gift(viewer, live, 100, "가".repeat(21), UUID.randomUUID().toString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("COMMON_001"));
        gift(viewer, live, 100, "가".repeat(20), UUID.randomUUID().toString()).andExpect(status().isCreated());
        // 900P밖에 없다.
        gift(viewer, live, 1_000, null, UUID.randomUUID().toString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("DONATION_002"));
        wallet(viewer).andExpect(jsonPath("$.data.balance").value(900));
    }

    @Test
    @DisplayName("단골 판매자에게도 그 판매자와의 거래로 받은 포인트를 선물할 수 있고, 판매자 한 명에게는 한 달 5,000P까지만 선물할 수 있다")
    void limits() throws Exception {
        Member viewer = signupAndLogin();
        Member seller = signupAndLogin();
        Member otherSeller = signupAndLogin();
        Long live = liveStream(seller, true);
        // 이 판매자(단골)와의 거래로 1,000P → 이 판매자에게 그대로 선물할 수 있다.
        donationPointService.earn(ORDER_SEQ.incrementAndGet(), viewer.id(), seller.id(), 100_000L);
        gift(viewer, live, 500, null, UUID.randomUUID().toString()).andExpect(status().isCreated());

        // 다른 판매자와의 거래로 6,000P 더
        for (int i = 0; i < 6; i++) {
            donationPointService.earn(ORDER_SEQ.incrementAndGet(), viewer.id(), otherSeller.id(), 100_000L);
        }
        gift(viewer, live, 4_500, null, UUID.randomUUID().toString()).andExpect(status().isCreated());
        gift(viewer, live, 100, null, UUID.randomUUID().toString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("DONATION_003"))
                .andExpect(jsonPath("$.error.message").value("이 판매자에게 이번 달 선물할 수 있는 마일리지는 0M 남았습니다."));
        // 남은 2,000P는 다른 판매자에게 선물할 수 있다.
        mockMvc.perform(get("/api/v1/live-streams/{id}/donations/me", live).header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
                .andExpect(jsonPath("$.data.balance").value(2_000))
                .andExpect(jsonPath("$.data.givenThisMonth").value(5_000))
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
        wallet(viewer).andExpect(jsonPath("$.data.balance").value(1_000))
                .andExpect(jsonPath("$.data.expiringSoon").value(600));

        assertThat(expirer.expire()).isGreaterThanOrEqualTo(400);
        wallet(viewer).andExpect(jsonPath("$.data.balance").value(600));
        mockMvc.perform(get("/api/v1/donation-points/histories").header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
                .andExpect(jsonPath("$.data.content[0].type").value("EXPIRE"))
                .andExpect(jsonPath("$.data.content[0].amount").value(400));

        expirer.notifyExpiring();
        expirer.notifyExpiring();
        verify(stompBroadcaster, after(500).times(1)).sendToUser(eq(viewer.id()), eq("/queue/notifications"), argThat(message ->
                message instanceof NotificationResponse n && n.type() == NotificationType.DONATION_POINT_EXPIRING
                        && n.title().equals("도네이션 마일리지 600M이 곧 사라져요")));
    }

    @Test
    @DisplayName("적립 이벤트 처리가 실패한 구매 확정 주문은 새벽 보정에서 구매자·판매자에게 한 번씩만 적립한다")
    void backfill() throws Exception {
        Member buyer = signupAndLogin();
        Member seller = signupAndLogin();
        // 이벤트 없이 구매 확정된 주문 (적립 이벤트가 실패한 것과 같다). 보정은 10분 넘게 지난 주문만 본다.
        Order order = Order.create(ORDER_SEQ.incrementAndGet(), buyer.id(), seller.id(), "보정 상품", null, 50_000L);
        order.registerDeliveryAddress("홍길동", "01012345678", "서울시 강남구 테헤란로 1", null);
        order.ship(Courier.CJ, "1234567890");
        order.confirm();
        Long orderId = orderRepository.save(order).getId();
        jdbcTemplate.update("update orders set completed_at = ? where id = ?", LocalDateTime.now().minusHours(1), orderId);

        assertThat(earner.backfill()).isGreaterThanOrEqualTo(2);
        wallet(buyer).andExpect(jsonPath("$.data.balance").value(500));
        wallet(seller).andExpect(jsonPath("$.data.balance").value(500));
        earner.backfill();
        wallet(buyer).andExpect(jsonPath("$.data.balance").value(500));
        wallet(seller).andExpect(jsonPath("$.data.balance").value(500));
    }

    @Test
    @DisplayName("관리자가 정책을 바꾸면 재시작 없이 바로 적용되고, 바꾼 기록이 남는다. 말이 안 되는 값은 거부한다")
    void policy() throws Exception {
        Member viewer = signupAndLogin();
        Member seller = signupAndLogin();
        Member otherSeller = signupAndLogin();
        Long live = liveStream(seller, true);
        donationPointService.earn(ORDER_SEQ.incrementAndGet(), viewer.id(), otherSeller.id(), 100_000L);

        // 관리자만 볼 수 있다.
        mockMvc.perform(get("/api/v1/admin/donation-policy").header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/donation-policy").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(jsonPath("$.data.policy.minGift").value(100))
                .andExpect(jsonPath("$.data.policy.monthlyGiftLimitPerSeller").value(5_000));

        // 최소 선물이 선물 단위로 나누어떨어지지 않으면 거부
        changePolicy(150, 5_000, "잘못된 값").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DONATION_011"));
        try {
            changePolicy(100, 300, "판매자당 한 달 300P로 낮춤").andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.usingDefaults").value(false))
                    .andExpect(jsonPath("$.data.policy.monthlyGiftLimitPerSeller").value(300));

            // 바로 적용된다.
            mockMvc.perform(get("/api/v1/live-streams/{id}/donations/me", live).header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
                    .andExpect(jsonPath("$.data.monthlyLimit").value(300));
            gift(viewer, live, 400, null, UUID.randomUUID().toString())
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("DONATION_003"));
            gift(viewer, live, 300, null, UUID.randomUUID().toString()).andExpect(status().isCreated());

            mockMvc.perform(get("/api/v1/admin/donation-policy/history").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                    .andExpect(jsonPath("$.data.content[0].memo").value("판매자당 한 달 300P로 낮춤"))
                    .andExpect(jsonPath("$.data.content[0].changedBy").value(900_002));
        } finally {
            // 다른 테스트가 기본 정책으로 돌도록 되돌린다.
            changePolicy(100, 5_000, "테스트 뒤 기본값으로").andExpect(status().isOk());
        }
    }

    private ResultActions changePolicy(long minGift, long monthlyGiftLimitPerSeller, String memo) throws Exception {
        String body = """
                {"earnRatePercent": 1, "maxEarnPerOrder": 1000, "monthlyEarnLimit": 10000, "pointValidDays": 90, "expiringNoticeDays": 7,
                 "minGift": %d, "giftUnit": 100, "maxGift": 5000, "monthlyGiftLimitPerSeller": %d, "trustWindowDays": 90,
                 "memo": "%s"}
                """.formatted(minGift, monthlyGiftLimitPerSeller, memo);
        return mockMvc.perform(put("/api/v1/admin/donation-policy").header(HttpHeaders.AUTHORIZATION, adminBearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // Access Token의 권한 클레임만으로 인증하므로, 관리자 권한을 가진 토큰을 직접 만든다.
    private String adminBearer() {
        User admin = User.create("donation-admin@test.com", "encoded", "관리자");
        ReflectionTestUtils.setField(admin, "id", 900_002L);
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);
        return "Bearer " + jwtProvider.createAccessToken(admin);
    }

    private ResultActions gift(Member giver, Long liveStreamId, long amount, String message, String key) throws Exception {
        String body = message == null
                ? "{\"amount\": %d, \"requestKey\": \"%s\"}".formatted(amount, key)
                : "{\"amount\": %d, \"message\": \"%s\", \"requestKey\": \"%s\"}".formatted(amount, message, key);
        return mockMvc.perform(post("/api/v1/live-streams/{id}/donations", liveStreamId)
                .header(HttpHeaders.AUTHORIZATION, giver.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions wallet(Member member) throws Exception {
        return mockMvc.perform(get("/api/v1/donation-points/wallet").header(HttpHeaders.AUTHORIZATION, member.bearer()))
                .andExpect(status().isOk());
    }

    /** 유효기간을 정해 적립 묶음을 바로 넣는다. (지갑 잔액도 함께 올린다) */
    private void lot(Member owner, Member seller, long amount, LocalDateTime expiresAt) {
        walletLocker.prepare(owner.id());
        transactionTemplate.executeWithoutResult(status -> {
            walletLocker.lock(owner.id()).add(amount);
            lotRepository.save(DonationPointLot.purchase(owner.id(), ORDER_SEQ.incrementAndGet(), seller.id(), amount,
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
