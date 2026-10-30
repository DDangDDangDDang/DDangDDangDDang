package com.example.ddangddangddang.domain.order.controller;

import com.example.ddangddangddang.domain.auth.repository.LoginAttemptRepository;
import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.order.dto.OrderCreateCommand;
import com.example.ddangddangddang.domain.order.service.OrderService;
import com.example.ddangddangddang.domain.order.entity.Courier;
import com.example.ddangddangddang.domain.order.exception.OrderErrorCode;
import com.example.ddangddangddang.domain.order.infra.DeliveryTrackingClient;
import com.example.ddangddangddang.domain.order.infra.TrackingCacheRepository;
import com.example.ddangddangddang.domain.order.infra.TrackingEvent;
import com.example.ddangddangddang.domain.order.infra.TrackingInfo;
import com.example.ddangddangddang.global.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import com.example.ddangddangddang.support.SignupFixture;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderService orderService;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    // 로그인 시도 횟수(Redis). 기본값 0이라 제한에 걸리지 않는다.
    @MockitoBean
    private LoginAttemptRepository loginAttemptRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @MockitoBean
    private VerificationService verificationService;

    // 실제 배송 조회 서비스·Redis 없이 실행하기 위해 Mock으로 대체
    @MockitoBean
    private DeliveryTrackingClient deliveryTrackingClient;

    @MockitoBean
    private TrackingCacheRepository trackingCacheRepository;

    private TestUser buyer;
    private TestUser seller;
    private TestUser stranger;

    @BeforeEach
    void setUp() throws Exception {
        SignupFixture.stubPhoneVerification(verificationService);
        buyer = signupAndLogin("buyer@test.com", "구매자");
        seller = signupAndLogin("seller@test.com", "판매자");
        stranger = signupAndLogin("stranger@test.com", "제삼자");
    }

    @Nested
    @DisplayName("GET /api/v1/orders")
    class GetOrders {

        @Test
        @DisplayName("구매자는 구매 내역, 판매자는 판매 내역에서 같은 주문을 본다")
        void buyerAndSeller() throws Exception {
            createOrder(1L, "아이패드");

            mockMvc.perform(get("/api/v1/orders").param("role", "BUYER").header(HttpHeaders.AUTHORIZATION, buyer.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.content[0].productName").value("아이패드"))
                    .andExpect(jsonPath("$.data.content[0].status").value("ESCROW"));

            mockMvc.perform(get("/api/v1/orders").param("role", "SELLER").header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                    .andExpect(jsonPath("$.data.content.length()").value(1));

            // 판매자의 구매 내역에는 없다
            mockMvc.perform(get("/api/v1/orders").param("role", "BUYER").header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                    .andExpect(jsonPath("$.data.content.length()").value(0));
        }

        @Test
        @DisplayName("최신순으로 size개씩 조회하고 nextCursor로 다음 페이지를 조회한다")
        void cursorPaging() throws Exception {
            createOrder(1L, "상품1");
            createOrder(2L, "상품2");
            createOrder(3L, "상품3");

            String firstPage = mockMvc.perform(get("/api/v1/orders").param("size", "2")
                            .header(HttpHeaders.AUTHORIZATION, buyer.bearer()))
                    .andExpect(jsonPath("$.data.content[0].productName").value("상품3"))
                    .andExpect(jsonPath("$.data.content[1].productName").value("상품2"))
                    .andExpect(jsonPath("$.data.hasNext").value(true))
                    .andReturn().getResponse().getContentAsString();
            Number nextCursor = JsonPath.read(firstPage, "$.data.nextCursor");

            mockMvc.perform(get("/api/v1/orders").param("size", "2").param("cursor", String.valueOf(nextCursor))
                            .header(HttpHeaders.AUTHORIZATION, buyer.bearer()))
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.content[0].productName").value("상품1"))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }

        @Test
        @DisplayName("role 값이 잘못되면 400 COMMON_001")
        void invalidRole() throws Exception {
            mockMvc.perform(get("/api/v1/orders").param("role", "WRONG")
                            .header(HttpHeaders.AUTHORIZATION, buyer.bearer()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_001"));
        }

        @Test
        @DisplayName("status로 필터링할 수 있다")
        void filterByStatus() throws Exception {
            createOrder(1L, "아이패드");

            mockMvc.perform(get("/api/v1/orders").param("status", "COMPLETED")
                            .header(HttpHeaders.AUTHORIZATION, buyer.bearer()))
                    .andExpect(jsonPath("$.data.content.length()").value(0));
        }
    }

    @Nested
    @DisplayName("배송지 입력")
    class DeliveryAddress {

        @Test
        @DisplayName("구매자가 배송지를 입력하면 상세 조회에서 구매자와 판매자 모두 볼 수 있다")
        void register() throws Exception {
            Long orderId = createOrder(1L, "아이패드");

            registerAddress(orderId, buyer)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.receiverName").value("홍길동"))
                    .andExpect(jsonPath("$.data.receiverPhone").value("01012345678"))
                    .andExpect(jsonPath("$.data.address").value("서울시 강남구 테헤란로 1"))
                    .andExpect(jsonPath("$.data.deliveryMemo").value("문 앞에 놓아주세요"));

            mockMvc.perform(get("/api/v1/orders/{orderId}", orderId).header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                    .andExpect(jsonPath("$.data.address").value("서울시 강남구 테헤란로 1"));
        }

        @Test
        @DisplayName("주문 목록에는 배송지가 나오지 않는다")
        void notInList() throws Exception {
            Long orderId = createOrder(1L, "아이패드");
            registerAddress(orderId, buyer);

            mockMvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, buyer.bearer()))
                    .andExpect(jsonPath("$.data.content[0].address").doesNotExist())
                    .andExpect(jsonPath("$.data.content[0].receiverPhone").doesNotExist());
        }

        @Test
        @DisplayName("판매자가 배송지를 입력하면 403 ORDER_008")
        void sellerCannotRegister() throws Exception {
            Long orderId = createOrder(1L, "아이패드");

            registerAddress(orderId, seller)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("ORDER_008"));
        }

        @Test
        @DisplayName("연락처에 하이픈이 있으면 400")
        void invalidPhone() throws Exception {
            Long orderId = createOrder(1L, "아이패드");

            mockMvc.perform(patch("/api/v1/orders/{orderId}/address", orderId)
                            .header(HttpHeaders.AUTHORIZATION, buyer.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"receiverName": "홍길동", "receiverPhone": "010-1234-5678", "address": "서울시"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_001"));
        }

        @Test
        @DisplayName("발송한 뒤에 배송지를 바꾸면 409 ORDER_009")
        void afterShip() throws Exception {
            Long orderId = createOrder(1L, "아이패드");
            registerAddress(orderId, buyer);
            ship(orderId, "CJ", "1234567890");

            registerAddress(orderId, buyer)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("ORDER_009"));
        }
    }

    @Nested
    @DisplayName("발송 -> 구매 확정")
    class ShipAndConfirm {

        @Test
        @DisplayName("구매자가 배송지를 입력하고, 판매자가 발송하고, 구매자가 확정하면 판매자 지갑에 낙찰 금액이 정산된다")
        void flow() throws Exception {
            Long orderId = createOrder(1L, "아이패드");
            registerAddress(orderId, buyer);

            ship(orderId, "CJ", "1234567890")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("SHIPPING"))
                    .andExpect(jsonPath("$.data.courier").value("CJ"))
                    .andExpect(jsonPath("$.data.trackingNo").value("1234567890"));

            mockMvc.perform(patch("/api/v1/orders/{orderId}/confirm", orderId)
                            .header(HttpHeaders.AUTHORIZATION, buyer.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"));

            mockMvc.perform(get("/api/v1/points/wallet").header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                    .andExpect(jsonPath("$.data.balance").value(500_000));
            mockMvc.perform(get("/api/v1/points/histories").header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                    .andExpect(jsonPath("$.data.content[0].type").value("SETTLE"))
                    .andExpect(jsonPath("$.data.content[0].refType").value("ORDER"))
                    .andExpect(jsonPath("$.data.content[0].refId").value(orderId));
        }

        @Test
        @DisplayName("구매자가 발송 처리하면 403 ORDER_004")
        void buyerCannotShip() throws Exception {
            Long orderId = createOrder(1L, "아이패드");
            registerAddress(orderId, buyer);

            mockMvc.perform(patch("/api/v1/orders/{orderId}/ship", orderId)
                            .header(HttpHeaders.AUTHORIZATION, buyer.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"courier": "CJ", "trackingNo": "1234567890"}
                                    """))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("ORDER_004"));
        }

        @Test
        @DisplayName("배송지가 입력되기 전에 발송하면 409 ORDER_010")
        void shipWithoutAddress() throws Exception {
            Long orderId = createOrder(1L, "아이패드");

            ship(orderId, "CJ", "1234567890")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("ORDER_010"));
        }

        @Test
        @DisplayName("다른 주문에 등록된 송장으로 발송하면 409 ORDER_011, 택배사가 다르면 허용")
        void duplicateTrackingNo() throws Exception {
            Long first = createOrder(1L, "아이패드");
            Long second = createOrder(2L, "맥북");
            registerAddress(first, buyer);
            registerAddress(second, buyer);
            ship(first, "CJ", "1234567890").andExpect(status().isOk());

            ship(second, "CJ", "1234567890")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("ORDER_011"));
            ship(second, "LOTTE", "1234567890")
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("송장번호가 없거나 하이픈이 있으면 400 COMMON_001")
        void invalidTrackingNo() throws Exception {
            Long orderId = createOrder(1L, "아이패드");
            registerAddress(orderId, buyer);

            ship(orderId, "CJ", "")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_001"));
            ship(orderId, "CJ", "1234-5678-90")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_001"));
        }

        @Test
        @DisplayName("택배사가 없으면 400 COMMON_001, 없는 택배사 코드면 400")
        void invalidCourier() throws Exception {
            Long orderId = createOrder(1L, "아이패드");
            registerAddress(orderId, buyer);

            mockMvc.perform(patch("/api/v1/orders/{orderId}/ship", orderId)
                            .header(HttpHeaders.AUTHORIZATION, seller.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"trackingNo": "1234567890"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_001"));
            ship(orderId, "UNKNOWN", "1234567890")
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("발송 전에 구매 확정하면 409 ORDER_007")
        void confirmBeforeShip() throws Exception {
            Long orderId = createOrder(1L, "아이패드");

            mockMvc.perform(patch("/api/v1/orders/{orderId}/confirm", orderId)
                            .header(HttpHeaders.AUTHORIZATION, buyer.bearer()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("ORDER_007"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders/{orderId}")
    class GetOrder {

        @Test
        @DisplayName("구매자와 판매자는 상세를 조회할 수 있다")
        void participant() throws Exception {
            Long orderId = createOrder(1L, "아이패드");

            mockMvc.perform(get("/api/v1/orders/{orderId}", orderId).header(HttpHeaders.AUTHORIZATION, buyer.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.finalPrice").value(500_000))
                    .andExpect(jsonPath("$.data.buyerId").value(buyer.id()))
                    .andExpect(jsonPath("$.data.sellerId").value(seller.id()));

            mockMvc.perform(get("/api/v1/orders/{orderId}", orderId).header(HttpHeaders.AUTHORIZATION, seller.bearer()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("거래 당사자가 아니면 403 ORDER_003")
        void stranger() throws Exception {
            Long orderId = createOrder(1L, "아이패드");

            mockMvc.perform(get("/api/v1/orders/{orderId}", orderId).header(HttpHeaders.AUTHORIZATION, stranger.bearer()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("ORDER_003"));
        }

        @Test
        @DisplayName("없는 주문이면 404 ORDER_001")
        void notFound() throws Exception {
            mockMvc.perform(get("/api/v1/orders/{orderId}", 9999L).header(HttpHeaders.AUTHORIZATION, buyer.bearer()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("ORDER_001"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders/{orderId}/tracking")
    class Tracking {

        @Test
        @DisplayName("발송한 주문은 구매자와 판매자 모두 배송 상태와 이력을 조회한다")
        void participants() throws Exception {
            Long orderId = shippedOrder();
            TrackingEvent pickup = new TrackingEvent("2026-10-25T20:00:00+09:00", "AT_PICKUP", "집화처리", "곤지암Hub", null);
            TrackingEvent out = new TrackingEvent("2026-10-26T18:10:00+09:00", "OUT_FOR_DELIVERY", "배송출발", "강남대리점", "배송기사 출발");
            given(trackingCacheRepository.find(any(), anyString())).willReturn(Optional.empty());
            given(deliveryTrackingClient.track(Courier.CJ, "1234567890")).willReturn(new TrackingInfo(out, List.of(pickup, out)));

            for (TestUser user : List.of(buyer, seller)) {
                mockMvc.perform(get("/api/v1/orders/{orderId}/tracking", orderId).header(HttpHeaders.AUTHORIZATION, user.bearer()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.courier").value("CJ"))
                        .andExpect(jsonPath("$.data.trackingNo").value("1234567890"))
                        .andExpect(jsonPath("$.data.lastEvent.statusCode").value("OUT_FOR_DELIVERY"))
                        .andExpect(jsonPath("$.data.lastEvent.location").value("강남대리점"))
                        .andExpect(jsonPath("$.data.events.length()").value(2))
                        .andExpect(jsonPath("$.data.events[0].statusName").value("집화처리"));
            }
        }

        @Test
        @DisplayName("다른 회원은 403 ORDER_003")
        void stranger() throws Exception {
            Long orderId = shippedOrder();

            mockMvc.perform(get("/api/v1/orders/{orderId}/tracking", orderId).header(HttpHeaders.AUTHORIZATION, stranger.bearer()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("ORDER_003"));
        }

        @Test
        @DisplayName("발송 전이면 400 ORDER_012")
        void beforeShip() throws Exception {
            Long orderId = createOrder(1L, "아이패드");

            mockMvc.perform(get("/api/v1/orders/{orderId}/tracking", orderId).header(HttpHeaders.AUTHORIZATION, buyer.bearer()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("ORDER_012"));
        }

        @Test
        @DisplayName("송장이 아직 택배사에 없으면 404 ORDER_014")
        void notFound() throws Exception {
            Long orderId = shippedOrder();
            given(trackingCacheRepository.find(any(), anyString())).willReturn(Optional.empty());
            given(deliveryTrackingClient.track(Courier.CJ, "1234567890"))
                    .willThrow(new BusinessException(OrderErrorCode.TRACKING_NOT_FOUND));

            mockMvc.perform(get("/api/v1/orders/{orderId}/tracking", orderId).header(HttpHeaders.AUTHORIZATION, buyer.bearer()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("ORDER_014"));
        }

        private Long shippedOrder() throws Exception {
            Long orderId = createOrder(1L, "아이패드");
            registerAddress(orderId, buyer).andExpect(status().isOk());
            ship(orderId, "CJ", "1234567890").andExpect(status().isOk());
            return orderId;
        }
    }

    private ResultActions registerAddress(Long orderId, TestUser user) throws Exception {
        return mockMvc.perform(patch("/api/v1/orders/{orderId}/address", orderId)
                .header(HttpHeaders.AUTHORIZATION, user.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"receiverName": "홍길동", "receiverPhone": "01012345678",
                         "address": "서울시 강남구 테헤란로 1", "deliveryMemo": "문 앞에 놓아주세요"}
                        """));
    }

    private ResultActions ship(Long orderId, String courier, String trackingNo) throws Exception {
        return mockMvc.perform(patch("/api/v1/orders/{orderId}/ship", orderId)
                .header(HttpHeaders.AUTHORIZATION, seller.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"courier": "%s", "trackingNo": "%s"}
                        """.formatted(courier, trackingNo)));
    }

    private Long createOrder(Long auctionId, String productName) {
        return orderService.createOrder(new OrderCreateCommand(
                auctionId, buyer.id(), seller.id(), productName, "https://img/1.png", 500_000L));
    }

    private TestUser signupAndLogin(String email, String nickname) throws Exception {
        String signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SignupFixture.signupBody(email, "Abcd1234!", nickname)))
                .andReturn().getResponse().getContentAsString();
        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "Abcd1234!"}
                                """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return new TestUser(((Number) JsonPath.read(signup, "$.data.userId")).longValue(),
                JsonPath.read(login, "$.data.accessToken"));
    }

    private record TestUser(Long id, String accessToken) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }
}