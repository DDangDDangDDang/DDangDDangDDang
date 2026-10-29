package com.example.ddangddangddang.domain.review.controller;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.livestream.infra.MediaServerClient;
import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.entity.OrderStatus;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 판매자 리뷰: 작성·수정·삭제, 판매자 답글, 상점 리뷰 탭 목록
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewControllerTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    // 주문의 경매 ID는 겹치면 안 되므로(유니크) 실행할 때마다 다른 큰 수에서 시작한다.
    private static final AtomicLong AUCTION_SEQ = new AtomicLong(System.currentTimeMillis());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

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
    @DisplayName("구매 확정한 구매자가 리뷰를 쓰고 고치면, 판매자가 답글을 달고 지울 수 있고, 리뷰를 지우면 목록에서 빠진다")
    void reviewFlow() throws Exception {
        Member buyer = signupAndLogin();
        Member seller = signupAndLogin();
        Long orderId = completedOrder(buyer, seller, "슈프림 후드");

        // 작성: 물품명은 주문에서 가져온다.
        Long reviewId = longValue(writeReview(buyer, orderId, 5, "설명대로 정품이고 상태도 좋아요.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sellerId").value(seller.id()))
                .andExpect(jsonPath("$.data.productName").value("슈프림 후드"))
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.reply").doesNotExist()), "$.data.reviewId");

        // 수정
        mockMvc.perform(patch("/api/v1/reviews/{id}", reviewId).header(HttpHeaders.AUTHORIZATION, buyer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\": 4, \"content\": \"배송은 조금 늦었어요.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(4))
                .andExpect(jsonPath("$.data.content").value("배송은 조금 늦었어요."));

        // 판매자 답글 달기 → 다시 달면 덮어쓴다.
        reply(seller, reviewId, "구매해 주셔서 감사합니다!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reply.content").value("구매해 주셔서 감사합니다!"))
                .andExpect(jsonPath("$.data.reply.repliedAt").exists());
        reply(seller, reviewId, "다음에도 찾아 주세요.")
                .andExpect(jsonPath("$.data.reply.content").value("다음에도 찾아 주세요."));

        // 로그인하지 않아도 상점 리뷰 탭을 볼 수 있다. 작성일은 답글을 달아도 리뷰를 쓴 시각 그대로다.
        String listed = mockMvc.perform(sellerReviews(seller.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].reviewId").value(reviewId))
                .andExpect(jsonPath("$.data.content[0].reply.content").value("다음에도 찾아 주세요."))
                .andReturn().getResponse().getContentAsString();
        String createdAt = JsonPath.read(listed, "$.data.content[0].createdAt");
        assertThat(createdAt).isNotNull().isNotEqualTo(JsonPath.read(listed, "$.data.content[0].reply.repliedAt"));

        // 답글 삭제 → 리뷰는 남고 답글만 빠진다.
        mockMvc.perform(delete("/api/v1/reviews/{id}/reply", reviewId).header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(sellerReviews(seller.id()))
                .andExpect(jsonPath("$.data.content[0].reviewId").value(reviewId))
                .andExpect(jsonPath("$.data.content[0].reply").doesNotExist());

        // 리뷰 삭제 → 목록에서 빠진다.
        mockMvc.perform(delete("/api/v1/reviews/{id}", reviewId).header(HttpHeaders.AUTHORIZATION, buyer.bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(sellerReviews(seller.id()))
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("남의 주문은 REVIEW_006, 구매 확정 전은 REVIEW_003, 두 번 쓰면 REVIEW_004, 없는 주문은 ORDER_001, 점수가 1~5가 아니면 400, 로그인하지 않으면 401")
    void createErrors() throws Exception {
        Member buyer = signupAndLogin();
        Member seller = signupAndLogin();
        Member other = signupAndLogin();
        Long completed = completedOrder(buyer, seller, "나이키 덩크");
        Long escrow = order(buyer, seller, "아직 배송 중");

        writeReview(other, completed, 5, "남의 주문").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("REVIEW_006"));
        writeReview(buyer, escrow, 5, "구매 확정 전").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REVIEW_003"));
        writeReview(buyer, 999_999_999L, 5, "없는 주문").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ORDER_001"));
        writeReview(buyer, completed, 6, "점수 초과").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));

        writeReview(buyer, completed, 5, "첫 리뷰").andExpect(status().isCreated());
        writeReview(buyer, completed, 4, "두 번째").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REVIEW_004"));

        mockMvc.perform(post("/api/v1/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(completed, 5, "비로그인")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("작성자가 아니면 수정·삭제할 수 없고(REVIEW_002), 리뷰 받은 판매자가 아니면 답글을 못 달며(REVIEW_005), 없는 리뷰는 REVIEW_001, 없는 답글은 REVIEW_007")
    void permissionErrors() throws Exception {
        Member buyer = signupAndLogin();
        Member seller = signupAndLogin();
        Member other = signupAndLogin();
        Long reviewId = longValue(writeReview(buyer, completedOrder(buyer, seller, "카메라"), 5, "좋아요"), "$.data.reviewId");

        mockMvc.perform(patch("/api/v1/reviews/{id}", reviewId).header(HttpHeaders.AUTHORIZATION, other.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\": 1, \"content\": \"남이 고침\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("REVIEW_002"));
        mockMvc.perform(delete("/api/v1/reviews/{id}", reviewId).header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("REVIEW_002"));

        // 구매자도 판매자가 아니므로 답글을 달 수 없다.
        reply(buyer, reviewId, "제가 답글").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("REVIEW_005"));
        mockMvc.perform(delete("/api/v1/reviews/{id}/reply", reviewId).header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REVIEW_007"));

        reply(seller, 999_999_999L, "없는 리뷰").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REVIEW_001"));
    }

    @Test
    @DisplayName("상점 리뷰 탭은 최신 리뷰부터 커서로 넘겨 보고, 빈 내용은 null로 저장되며, 없는 판매자는 USER_003")
    void sellerReviewsCursor() throws Exception {
        Member buyer = signupAndLogin();
        Member seller = signupAndLogin();
        Long first = longValue(writeReview(buyer, completedOrder(buyer, seller, "렌즈"), 3, "   "), "$.data.reviewId");
        Long second = longValue(writeReview(buyer, completedOrder(buyer, seller, "삼각대"), 5, "튼튼해요"), "$.data.reviewId");

        Long cursor = longValue(mockMvc.perform(sellerReviews(seller.id()).param("size", "1"))
                .andExpect(jsonPath("$.data.content[0].reviewId").value(second))
                .andExpect(jsonPath("$.data.hasNext").value(true)), "$.data.nextCursor");
        mockMvc.perform(sellerReviews(seller.id()).param("size", "1").param("cursor", String.valueOf(cursor)))
                .andExpect(jsonPath("$.data.content[0].reviewId").value(first))
                .andExpect(jsonPath("$.data.content[0].content").doesNotExist())
                // 답글이 없는 리뷰도 작성일이 나온다.
                .andExpect(jsonPath("$.data.content[0].createdAt").exists())
                .andExpect(jsonPath("$.data.hasNext").value(false));

        mockMvc.perform(sellerReviews(999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_003"));
    }

    @Test
    @DisplayName("판매자 프로필에 구매자 만족도(평균 점수, 리뷰 수)가 나오고, 리뷰가 없으면 평균은 null")
    void profileSatisfaction() throws Exception {
        Member buyer = signupAndLogin();
        Member seller = signupAndLogin();

        // 리뷰가 없으면 평균은 null, 리뷰 수는 0
        mockMvc.perform(get("/api/v1/sellers/{id}/profile", seller.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trust.averageRating").doesNotExist())
                .andExpect(jsonPath("$.data.trust.reviewCount").value(0));

        // 5, 4, 4점 → 평균 4.333... → 소수 첫째 자리 4.3
        writeReview(buyer, completedOrder(buyer, seller, "렌즈"), 5, "좋아요").andExpect(status().isCreated());
        writeReview(buyer, completedOrder(buyer, seller, "삼각대"), 4, "괜찮아요").andExpect(status().isCreated());
        writeReview(buyer, completedOrder(buyer, seller, "카메라"), 4, "").andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/sellers/{id}/profile", seller.id()))
                .andExpect(jsonPath("$.data.trust.averageRating").value(4.3))
                .andExpect(jsonPath("$.data.trust.reviewCount").value(3));
    }

    private MockHttpServletRequestBuilder sellerReviews(Long sellerId) {
        return get("/api/v1/reviews").param("sellerId", String.valueOf(sellerId));
    }

    private ResultActions writeReview(Member member, Long orderId, int rating, String content) throws Exception {
        return mockMvc.perform(post("/api/v1/reviews").header(HttpHeaders.AUTHORIZATION, member.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(reviewBody(orderId, rating, content)));
    }

    private ResultActions reply(Member member, Long reviewId, String content) throws Exception {
        return mockMvc.perform(put("/api/v1/reviews/{id}/reply", reviewId).header(HttpHeaders.AUTHORIZATION, member.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"%s\"}".formatted(content)));
    }

    private String reviewBody(Long orderId, int rating, String content) {
        return "{\"orderId\": %d, \"rating\": %d, \"content\": \"%s\"}".formatted(orderId, rating, content);
    }

    // 결제가 끝난 주문 (ESCROW)
    private Long order(Member buyer, Member seller, String productName) {
        return orderRepository.save(Order.create(AUCTION_SEQ.incrementAndGet(), buyer.id(), seller.id(), productName, null, 50_000L)).getId();
    }

    // 구매 확정한 주문. 발송·확정 과정은 주문 테스트에서 검사하므로 상태만 바꾼다.
    private Long completedOrder(Member buyer, Member seller, String productName) {
        Order order = orderRepository.findById(order(buyer, seller, productName)).orElseThrow();
        ReflectionTestUtils.setField(order, "status", OrderStatus.COMPLETED);
        return orderRepository.save(order).getId();
    }

    private Long longValue(ResultActions result, String path) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
    }

    private Member signupAndLogin() throws Exception {
        int seq = SEQ.incrementAndGet();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String email = "review" + seq + "-" + suffix + "@test.com";
        String nickname = "리뷰" + seq + suffix.substring(0, 4);
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