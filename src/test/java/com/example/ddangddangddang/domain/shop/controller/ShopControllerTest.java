package com.example.ddangddangddang.domain.shop.controller;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.donation.service.DonationPointService;
import com.example.ddangddangddang.global.security.jwt.JwtProvider;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import com.example.ddangddangddang.support.SignupFixture;
import com.jayway.jsonpath.JsonPath;
import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.category.entity.Category;
import com.example.ddangddangddang.domain.category.repository.CategoryRepository;
import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.domain.product.repository.ProductRepository;
import com.example.ddangddangddang.domain.shop.entity.AuctionBoost;
import com.example.ddangddangddang.domain.shop.repository.AuctionBoostRepository;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.domain.donation.entity.DonationPointLot;
import com.example.ddangddangddang.domain.donation.repository.DonationPointLotRepository;
import com.example.ddangddangddang.domain.donation.service.DonationWalletLocker;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.order.entity.Courier;
import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import com.example.ddangddangddang.domain.seller.repository.SellerApplicationRepository;
import com.example.ddangddangddang.support.SellerFixture;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 마일리지 상점: 닉네임 변경권 (3,000M), 닉네임 뱃지 (각 5,000M), 경매 위로 올리기 (1,000M, 3시간, 동시 10개, 경매당 한 번),
 * 파워 판매자 (20,000M, 영구, 판매 확정 20건 이상인 인증 판매자)
 * 결제와 닉네임 변경은 한 트랜잭션이라, 변경이 실패하면 마일리지도 빠지지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShopControllerTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    // 적립 묶음의 (order_id, source)는 유니크라 테스트마다 다른 주문 번호를 쓴다. (주문 테이블과 연관관계는 없음)
    private static final AtomicLong ORDER_SEQ = new AtomicLong(System.nanoTime() % 1_000_000_000L + 2_000_000_000L);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DonationPointService donationPointService;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @MockitoBean
    private VerificationService verificationService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private AuctionBoostRepository boostRepository;

    @Autowired
    private SellerApplicationRepository sellerApplicationRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private LiveStreamRepository liveStreamRepository;

    @Autowired
    private DonationWalletLocker walletLocker;

    @Autowired
    private DonationPointLotRepository lotRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    // 위로 올린 경매가 남아 있으면 다른 테스트의 경매 목록 순서가 바뀌므로 지운다.
    @AfterEach
    void clearBoosts() {
        boostRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("닉네임 변경권은 변경 제한 기간 중에만 살 수 있고, 사면 바로 닉네임이 바뀌며 30일 제한이 다시 시작된다")
    void nicknameChange() throws Exception {
        Member member = signupAndLogin();

        // 가입 후 첫 변경은 무료라 변경권이 필요 없다.
        items(member).andExpect(jsonPath("$.data[0].item").value("NICKNAME_CHANGE"))
                .andExpect(jsonPath("$.data[0].price").value(3_000))
                .andExpect(jsonPath("$.data[0].available").value(false))
                .andExpect(jsonPath("$.data[0].unavailableReason").value("지금은 변경권 없이 닉네임을 바꿀 수 있어요."));
        buyNicknameChange(member.bearer(), "변경권" + member.seq())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("USER_012"));

        // 무료로 한 번 바꾸면 30일 제한 → 변경권을 살 수 있다.
        String freeChanged = mockMvc.perform(patch("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, member.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\": \"무료" + member.seq() + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(freeChanged, "$.data.accessToken");
        items("Bearer " + token).andExpect(jsonPath("$.data[0].available").value(true));

        // 마일리지가 모자라면 바꾸지 않는다. (닉네임을 바꾼 뒤 잔액을 확인하므로 한 트랜잭션으로 함께 되돌린다)
        buyNicknameChange("Bearer " + token, "변경권" + member.seq())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("DONATION_002"));
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.nickname").value("무료" + member.seq()));

        earn(member, 3_500);
        String bought = buyNicknameChange("Bearer " + token, "변경권" + member.seq())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.nickname").value("변경권" + member.seq()))
                .andExpect(jsonPath("$.data.user.nicknameChangeAvailableAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String newToken = JsonPath.read(bought, "$.data.accessToken");
        assertThat(jwtProvider.parseClaims(newToken).get(JwtProvider.CLAIM_NICKNAME, String.class)).isEqualTo("변경권" + member.seq());

        wallet("Bearer " + newToken).andExpect(jsonPath("$.data.balance").value(500));
        mockMvc.perform(get("/api/v1/donation-points/histories").header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken))
                .andExpect(jsonPath("$.data.content[0].type").value("SHOP"))
                .andExpect(jsonPath("$.data.content[0].amount").value(3_000))
                .andExpect(jsonPath("$.data.content[0].balanceAfter").value(500));
    }

    @Test
    @DisplayName("닉네임을 바꿀 수 없으면(같은 닉네임, 다른 회원이 사용 중) 마일리지도 빠지지 않는다")
    void failedChangeKeepsMileage() throws Exception {
        Member member = signupAndLogin();
        Member other = signupAndLogin();
        String token = changeNicknameFree(member, "무료" + member.seq());
        earn(member, 3_000);

        buyNicknameChange("Bearer " + token, "무료" + member.seq())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("USER_013"));
        buyNicknameChange("Bearer " + token, other.nickname())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("USER_002"));
        buyNicknameChange("Bearer " + token, "가")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("COMMON_001"));

        wallet("Bearer " + token).andExpect(jsonPath("$.data.balance").value(3_000));
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.nickname").value("무료" + member.seq()));
    }

    @Test
    @DisplayName("닉네임 뱃지는 사면 영구로 가지고 바로 장착된다. 같은 뱃지는 다시 살 수 없고, 가진 뱃지끼리 바꿔 끼거나 뺄 수 있다")
    void badges() throws Exception {
        Member member = signupAndLogin();
        items(member).andExpect(jsonPath("$.data.length()").value(7))
                .andExpect(jsonPath("$.data[4].item").value("BADGE_STAR"))
                .andExpect(jsonPath("$.data[4].badge").value("STAR"))
                .andExpect(jsonPath("$.data[4].price").value(5_000))
                .andExpect(jsonPath("$.data[4].available").value(true));

        // 마일리지가 모자라면 갖지 못한다.
        buyBadge(member, "STAR").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("DONATION_002"));
        myBadges(member).andExpect(jsonPath("$.data.owned.length()").value(0))
                .andExpect(jsonPath("$.data.equipped").doesNotExist());

        earn(member, 10_000);
        buyBadge(member, "STAR").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.owned[0]").value("STAR"))
                .andExpect(jsonPath("$.data.equipped").value("STAR"));
        // 이미 가진 뱃지는 다시 살 수 없고 마일리지도 빠지지 않는다.
        buyBadge(member, "STAR").andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("USER_014"));
        wallet(member.bearer()).andExpect(jsonPath("$.data.balance").value(5_000));
        items(member).andExpect(jsonPath("$.data[4].available").value(false))
                .andExpect(jsonPath("$.data[4].unavailableReason").value("이미 가지고 있어요. 내 뱃지에서 장착할 수 있어요."));

        buyBadge(member, "HALO").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.owned.length()").value(2))
                .andExpect(jsonPath("$.data.equipped").value("HALO"));
        wallet(member.bearer()).andExpect(jsonPath("$.data.balance").value(0));
        mockMvc.perform(get("/api/v1/donation-points/histories").header(HttpHeaders.AUTHORIZATION, member.bearer()))
                .andExpect(jsonPath("$.data.content[0].type").value("SHOP"))
                .andExpect(jsonPath("$.data.content[0].amount").value(5_000));

        // 가진 뱃지끼리 바꿔 끼기, 갖지 않은 뱃지는 안 됨, null이면 빼기
        equip(member, "\"STAR\"").andExpect(status().isOk()).andExpect(jsonPath("$.data.equipped").value("STAR"));
        equip(member, "\"DEVIL_WINGS\"").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("USER_015"));
        equip(member, "null").andExpect(status().isOk()).andExpect(jsonPath("$.data.equipped").doesNotExist());
        myBadges(member).andExpect(jsonPath("$.data.owned.length()").value(2))
                .andExpect(jsonPath("$.data.equipped").doesNotExist());
    }

    @Test
    @DisplayName("경매 위로 올리기: 내 시작 전·진행 중 경매만, 경매당 한 번. 올리면 3시간 동안 최신순 목록 맨 위에 '홍보'로 보인다")
    void auctionBoost() throws Exception {
        Member seller = signupAndLogin();
        Member other = signupAndLogin();
        Long boosted = auction(seller, "올릴 경매");
        Long newer = auction(other, "더 최근 경매");
        earn(seller, 3_000);

        boost(seller, newer).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("SHOP_004"));
        boost(seller, 999_999_999L).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("AUCTION_001"));

        String response = boost(seller, boosted).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auctionId").value(boosted))
                .andReturn().getResponse().getContentAsString();
        LocalDateTime startsAt = LocalDateTime.parse(JsonPath.read(response, "$.data.startsAt"));
        LocalDateTime endsAt = LocalDateTime.parse(JsonPath.read(response, "$.data.endsAt"));
        assertThat(Duration.between(startsAt, endsAt)).isEqualTo(Duration.ofHours(3));
        // 경매 하나는 한 번만 (마일리지는 빠지지 않음)
        boost(seller, boosted).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SHOP_002"));
        wallet(seller.bearer()).andExpect(jsonPath("$.data.balance").value(2_000));

        // 더 늦게 만든 경매보다 위에, '홍보'로 (최신순)
        mockMvc.perform(get("/api/v1/auctions"))
                .andExpect(jsonPath("$.data.content[0].auctionId").value(boosted))
                .andExpect(jsonPath("$.data.content[0].promoted").value(true))
                .andExpect(jsonPath("$.data.content[1].auctionId").value(newer))
                .andExpect(jsonPath("$.data.content[1].promoted").value(false));
    }

    @Test
    @DisplayName("이미 끝난 경매는 올릴 수 없고, 동시에 10개가 올라가 있으면 자리가 날 때까지 올릴 수 없다 (마일리지는 빠지지 않음)")
    void auctionBoostRules() throws Exception {
        Member seller = signupAndLogin();
        earn(seller, 3_000);
        Long canceled = auction(seller, "취소한 경매");
        Auction auction = auctionRepository.findById(canceled).orElseThrow();
        auction.cancel();
        auctionRepository.save(auction);
        boost(seller, canceled).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("SHOP_001"));

        // 다른 판매자들이 이미 10개를 올렸다. (가장 먼저 끝나는 것이 30분 뒤)
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < AuctionBoost.MAX_ACTIVE; i++) {
            boostRepository.save(AuctionBoost.start(-(i + 1L) - System.nanoTime() % 1_000_000, 0L, now.minusMinutes(150 - i)));
        }
        items(seller).andExpect(jsonPath("$.data[5].item").value("AUCTION_BOOST"))
                .andExpect(jsonPath("$.data[5].available").value(false))
                .andExpect(jsonPath("$.data[5].unavailableReason").value("지금은 위로 올릴 자리가 없어요. 약 30분 뒤에 올릴 수 있어요."));
        boost(seller, auction(seller, "자리 없는 경매"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SHOP_003"))
                .andExpect(jsonPath("$.error.message").value("지금은 위로 올릴 자리가 없어요. 약 30분 뒤에 올릴 수 있어요."));
        wallet(seller.bearer()).andExpect(jsonPath("$.data.balance").value(3_000));
    }

    @Test
    @DisplayName("파워 판매자는 판매 확정 20건 이상인 인증 판매자만 20,000M으로 영구로 한 번 산다. 프로필·방송 카드에 표시되고 라이브 목록 순서는 바꾸지 않는다")
    void powerSeller() throws Exception {
        Member seller = signupAndLogin();
        Member buyer = signupAndLogin();
        mileage(seller, 25_000);
        items(seller).andExpect(jsonPath("$.data[6].item").value("POWER_SELLER"))
                .andExpect(jsonPath("$.data[6].price").value(20_000))
                .andExpect(jsonPath("$.data[6].available").value(false))
                .andExpect(jsonPath("$.data[6].unavailableReason").value("판매자 인증을 받은 회원만 살 수 있어요."));
        buyPower(seller).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("DONATION_009"));

        // 인증했지만 판매 확정 19건
        SellerFixture.verify(sellerApplicationRepository, seller.id());
        completedSales(seller, buyer, 19);
        items(seller).andExpect(jsonPath("$.data[6].unavailableReason").value("판매 확정 20건 이상부터 살 수 있어요. (지금 19건)"));
        buyPower(seller).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("SHOP_005"))
                .andExpect(jsonPath("$.error.message").value("판매 확정 20건 이상부터 살 수 있어요. (지금 19건)"));

        completedSales(seller, buyer, 1);
        items(seller).andExpect(jsonPath("$.data[6].available").value(true));
        buyPower(seller).andExpect(status().isOk());
        wallet(seller.bearer()).andExpect(jsonPath("$.data.balance").value(5_000));
        mockMvc.perform(get("/api/v1/sellers/{id}/profile", seller.id())).andExpect(jsonPath("$.data.powerSeller").value(true));

        // 영구라 한 번만 (마일리지는 빠지지 않음)
        items(seller).andExpect(jsonPath("$.data[6].unavailableReason").value("이미 파워 판매자예요."));
        buyPower(seller).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SHOP_006"));
        wallet(seller.bearer()).andExpect(jsonPath("$.data.balance").value(5_000));

        // 방송 카드에 표시하지만, 라이브 목록 맨 위로 올리지는 않는다. (나중에 시작한 방송이 위)
        Long powerLive = liveStream(seller);
        Long laterLive = liveStream(buyer);
        String lives = mockMvc.perform(get("/api/v1/live-streams")).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(lives, "$.data.content[*].liveStreamId");
        assertThat(ids.indexOf(laterLive.intValue())).isLessThan(ids.indexOf(powerLive.intValue()));
        List<Boolean> power = JsonPath.read(lives, "$.data.content[?(@.liveStreamId == " + powerLive + ")].powerSeller");
        assertThat(power).containsExactly(true);
    }

    private ResultActions buyPower(Member member) throws Exception {
        return mockMvc.perform(post("/api/v1/shop/power-seller").header(HttpHeaders.AUTHORIZATION, member.bearer()));
    }

    /** 구매 확정된 판매를 count건 만든다. (주문만 저장, 적립 이벤트는 없음) */
    private void completedSales(Member seller, Member buyer, int count) {
        for (int i = 0; i < count; i++) {
            long seq = ORDER_SEQ.incrementAndGet();
            Order order = Order.create(seq, buyer.id(), seller.id(), "판매 " + seq, null, 10_000L);
            order.registerDeliveryAddress("홍길동", "01012345678", "서울시 강남구 테헤란로 1", null);
            order.ship(Courier.CJ, "PW" + seq);
            order.confirm();
            orderRepository.save(order);
        }
    }

    /** 마일리지를 바로 넣는다. (구매 적립은 한 달 10,000M 한도가 있어 묶음으로 직접 넣는다) */
    private void mileage(Member member, long amount) {
        walletLocker.prepare(member.id());
        transactionTemplate.executeWithoutResult(status -> {
            walletLocker.lock(member.id()).add(amount);
            LocalDateTime now = LocalDateTime.now();
            lotRepository.save(DonationPointLot.purchase(member.id(), ORDER_SEQ.incrementAndGet(), 999_999L, amount, now, now.plusDays(90)));
        });
    }

    /** 방송 중인 방송 (송출 서버는 쓰지 않는다) */
    private Long liveStream(Member seller) {
        LiveStream stream = LiveStream.create(seller.id(), "파워 방송 " + seller.seq());
        stream.start(seller.id());
        return liveStreamRepository.save(stream).getId();
    }

    private ResultActions boost(Member member, Long auctionId) throws Exception {
        return mockMvc.perform(post("/api/v1/shop/auction-boosts").header(HttpHeaders.AUTHORIZATION, member.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"auctionId\": " + auctionId + "}"));
    }

    /** 시작 전 경매 (내일 시작) */
    private Long auction(Member seller, String name) {
        Category parent = categoryRepository.save(new Category(null, "상점" + UUID.randomUUID().toString().substring(0, 6), 1));
        Category leaf = categoryRepository.save(new Category(parent, "카메라", 1));
        Product product = productRepository.save(new Product(userRepository.findById(seller.id()).orElseThrow(), leaf, name, "테스트 상품"));
        LocalDateTime startAt = LocalDateTime.now().plusDays(1).withNano(0);
        return auctionRepository.save(new Auction(product, null, 10_000L, 1_000L, null, startAt, startAt.plusDays(1))).getId();
    }

    private ResultActions buyBadge(Member member, String badge) throws Exception {
        return mockMvc.perform(post("/api/v1/shop/badges/{badge}", badge).header(HttpHeaders.AUTHORIZATION, member.bearer()));
    }

    private ResultActions myBadges(Member member) throws Exception {
        return mockMvc.perform(get("/api/v1/users/me/badges").header(HttpHeaders.AUTHORIZATION, member.bearer())).andExpect(status().isOk());
    }

    private ResultActions equip(Member member, String badgeJson) throws Exception {
        return mockMvc.perform(put("/api/v1/users/me/badge").header(HttpHeaders.AUTHORIZATION, member.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"badge\": " + badgeJson + "}"));
    }

    private String changeNicknameFree(Member member, String nickname) throws Exception {
        String response = mockMvc.perform(patch("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, member.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\": \"" + nickname + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.accessToken");
    }

    /** 구매 확정 적립으로 마일리지를 넣는다. (주문당 최대 1,000M이라 나눠서) */
    private void earn(Member member, long amount) {
        long left = amount;
        while (left > 0) {
            long points = Math.min(1_000, left);
            donationPointService.earn(ORDER_SEQ.incrementAndGet(), member.id(), 999_999L, points * 100);
            left -= points;
        }
    }

    private ResultActions items(Member member) throws Exception {
        return items(member.bearer());
    }

    private ResultActions items(String bearer) throws Exception {
        return mockMvc.perform(get("/api/v1/shop/items").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk());
    }

    private ResultActions buyNicknameChange(String bearer, String nickname) throws Exception {
        return mockMvc.perform(post("/api/v1/shop/nickname-change").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\": \"" + nickname + "\"}"));
    }

    private ResultActions wallet(String bearer) throws Exception {
        return mockMvc.perform(get("/api/v1/donation-points/wallet").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk());
    }

    private Member signupAndLogin() throws Exception {
        int seq = SEQ.incrementAndGet();
        String suffix = UUID.randomUUID().toString().substring(0, 4);
        String email = "shop" + seq + "-" + suffix + "@test.com";
        String nickname = "상점" + seq + suffix;
        SignupFixture.stubPhoneVerification(verificationService);
        String signup = mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(SignupFixture.signupBody(email, "Abcd1234!", nickname)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(signup, "$.data.userId")).longValue();
        String login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"Abcd1234!\"}"))
                .andReturn().getResponse().getContentAsString();
        return new Member(id, seq + suffix.substring(0, 2), nickname, JsonPath.read(login, "$.data.accessToken"));
    }

    private record Member(Long id, String seq, String nickname, String accessToken) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }
}
