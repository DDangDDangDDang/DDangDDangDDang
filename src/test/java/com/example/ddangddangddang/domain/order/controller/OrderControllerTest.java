package com.example.ddangddangddang.domain.order.controller;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.order.dto.OrderCreateCommand;
import com.example.ddangddangddang.domain.order.service.OrderService;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
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
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    private TestUser buyer;
    private TestUser seller;
    private TestUser stranger;

    @BeforeEach
    void setUp() throws Exception {
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

    private Long createOrder(Long auctionId, String productName) {
        return orderService.createOrder(new OrderCreateCommand(
                auctionId, buyer.id(), seller.id(), productName, "https://img/1.png", 500_000L));
    }

    private TestUser signupAndLogin(String email, String nickname) throws Exception {
        String signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "Abcd1234!", "nickname": "%s"}
                                """.formatted(email, nickname)))
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