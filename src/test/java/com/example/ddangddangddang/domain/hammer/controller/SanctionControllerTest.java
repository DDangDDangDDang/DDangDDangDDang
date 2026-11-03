package com.example.ddangddangddang.domain.hammer.controller;

import com.example.ddangddangddang.domain.auth.repository.LoginAttemptRepository;
import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.hammer.repository.SanctionReleaseRepository;
import com.example.ddangddangddang.domain.hammer.service.SanctionService;
import com.example.ddangddangddang.domain.livestream.infra.MediaServerClient;
import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.entity.OrderCancelReason;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.entity.UserRole;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.security.jwt.JwtProvider;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 내 정지 조회·정지 중 입찰 차단·관리자 정지 검토 API.
 * 다른 컨트롤러 테스트와 같은 설정(목 빈 목록)을 써서 스프링 컨텍스트를 함께 쓴다. 같은 DB를 쓰므로 만든 데이터만 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SanctionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private SanctionService sanctionService;
    @Autowired private SanctionReleaseRepository sanctionReleaseRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;

    @MockitoBean private RefreshTokenRepository refreshTokenRepository;
    @MockitoBean private LoginAttemptRepository loginAttemptRepository;
    @MockitoBean private TokenBlacklistRepository tokenBlacklistRepository;
    @MockitoBean private MediaServerClient mediaServerClient;
    @MockitoBean private VerificationService verificationService;
    @MockitoBean private StompBroadcaster stompBroadcaster;

    private final List<Long> orderIds = new ArrayList<>();
    private User buyer;
    private User seller;
    private Object originalCountedFrom;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        buyer = userRepository.save(User.create("sanction-buyer-" + suffix + "@test.com", "pw", "정지구매자" + suffix.substring(0, 4)));
        seller = userRepository.save(User.create("sanction-seller-" + suffix + "@test.com", "pw", "정지판매자" + suffix.substring(0, 4)));
        // 시행일 전 취소는 세지 않는다. 테스트에서는 아주 옛날로 두고, 끝나면 되돌린다 (컨텍스트를 함께 쓰는 다른 테스트에 영향이 없게)
        originalCountedFrom = ReflectionTestUtils.getField(sanctionService, "countedFrom");
        ReflectionTestUtils.setField(sanctionService, "countedFrom", LocalDateTime.of(2000, 1, 1, 0, 0));
        LocalDateTime now = LocalDateTime.now();
        for (int day = 4; day >= 1; day--) {
            Order order = Order.create(System.nanoTime(), buyer.getId(), seller.getId(), "상품", null, 10_000L);
            order.cancel(OrderCancelReason.BUYER_NO_ADDRESS, now.minusDays(day));
            orderIds.add(orderRepository.save(order).getId());
        }
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(sanctionService, "countedFrom", originalCountedFrom);
        sanctionReleaseRepository.deleteAll(sanctionReleaseRepository.findAll().stream()
                .filter(release -> release.getUserId().equals(buyer.getId())).toList());
        orderRepository.deleteAllById(orderIds);
        userRepository.deleteAllById(List.of(buyer.getId(), seller.getId()));
    }

    @Test
    @DisplayName("내 정지 조회, 정지 중 입찰은 403 SANCTION_001 (풀리는 시각 안내)")
    void mySanctionsAndBlockedBid() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/sanctions").header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("BID_BAN"))
                .andExpect(jsonPath("$.data[0].cancelCount").value(4))
                .andExpect(jsonPath("$.data[0].bannedUntil").exists());

        mockMvc.perform(post("/api/v1/auctions/{auctionId}/bids", 999_999L).header(HttpHeaders.AUTHORIZATION, bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 10000}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SANCTION_001"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("에 풀립니다")));
    }

    @Test
    @DisplayName("관리자는 검토 대상을 보고 정지를 풀 수 있고, 일반 회원은 403")
    void adminReviewAndRelease() throws Exception {
        mockMvc.perform(get("/api/v1/admin/sanctions").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(status().isForbidden());

        String target = "$.data[?(@.userId == " + buyer.getId() + ")]";
        mockMvc.perform(get("/api/v1/admin/sanctions").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(target + ".nickname").value(buyer.getNickname()))
                .andExpect(jsonPath(target + ".type").value("BID_BAN"))
                .andExpect(jsonPath(target + ".cancelCount").value(4));

        mockMvc.perform(post("/api/v1/admin/sanctions/release").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + buyer.getId() + ", \"type\": \"BID_BAN\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me/sanctions").header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user);
    }

    private String adminBearer() {
        User admin = User.create("sanction-admin@test.com", "encoded", "관리자");
        ReflectionTestUtils.setField(admin, "id", 900_002L);
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);
        return "Bearer " + jwtProvider.createAccessToken(admin);
    }
}
