package com.example.ddangddangddang.domain.point.controller;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.point.entity.PointHistory;
import com.example.ddangddangddang.domain.point.entity.PointHistoryType;
import com.example.ddangddangddang.domain.point.entity.PointReferenceType;
import com.example.ddangddangddang.domain.point.entity.PointWallet;
import com.example.ddangddangddang.domain.point.repository.PointHistoryRepository;
import com.example.ddangddangddang.domain.point.repository.PointWalletRepository;
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
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PointWalletRepository pointWalletRepository;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @MockitoBean
    private VerificationService verificationService;

    private String accessToken;
    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        SignupFixture.stubPhoneVerification(verificationService);
        String signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SignupFixture.signupBody("user@test.com", "Abcd1234!", "경매왕")))
                .andReturn().getResponse().getContentAsString();

        userId = ((Number) JsonPath.read(signup, "$.data.userId")).longValue();

        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "user@test.com", "password": "Abcd1234!"}
                                """))
                .andReturn().getResponse().getContentAsString();
        accessToken = JsonPath.read(login, "$.data.accessToken");
    }

    @Nested
    @DisplayName("GET /api/v1/points/wallet")
    class GetWallet {

        @Test
        @DisplayName("회원가입 직후에는 잔액 0인 지갑이 조회된다")
        void afterSignup() throws Exception {
            mockMvc.perform(get("/api/v1/points/wallet")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.balance").value(0))
                    .andExpect(jsonPath("$.data.heldBalance").value(0))
                    .andExpect(jsonPath("$.data.totalBalance").value(0));
        }

        @Test
        @DisplayName("토큰 없이 요청하면 401 AUTH_001")
        void noToken() throws Exception {
            mockMvc.perform(get("/api/v1/points/wallet"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("AUTH_001"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/points/histories")
    class GetHistories {

        @Test
        @DisplayName("최신순으로 size개씩 조회하고, nextCursor로 다음 페이지를 조회한다")
        void cursorPaging() throws Exception {
            PointWallet wallet = pointWalletRepository.findByUserId(userId).orElseThrow();
            for (long i = 1; i <= 3; i++) {
                pointHistoryRepository.save(PointHistory.of(wallet, PointHistoryType.CHARGE, i * 1_000, PointReferenceType.CHARGE, i));
            }

            String firstPage = mockMvc.perform(get("/api/v1/points/histories")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(2))
                    .andExpect(jsonPath("$.data.content[0].refId").value(3))
                    .andExpect(jsonPath("$.data.content[1].refId").value(2))
                    .andExpect(jsonPath("$.data.hasNext").value(true))
                    .andReturn().getResponse().getContentAsString();
            Number nextCursor = JsonPath.read(firstPage, "$.data.nextCursor");

            mockMvc.perform(get("/api/v1/points/histories")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                            .param("size", "2")
                            .param("cursor", String.valueOf(nextCursor)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.content[0].refId").value(1))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }

        @Test
        @DisplayName("type으로 필터링할 수 있다")
        void filterByType() throws Exception {
            PointWallet wallet = pointWalletRepository.findByUserId(userId).orElseThrow();
            pointHistoryRepository.save(PointHistory.of(wallet, PointHistoryType.CHARGE, 1_000, PointReferenceType.CHARGE, 1L));
            pointHistoryRepository.save(PointHistory.of(wallet, PointHistoryType.HOLD, 500, PointReferenceType.AUCTION, 1L));

            mockMvc.perform(get("/api/v1/points/histories")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                            .param("type", "HOLD"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.content[0].type").value("HOLD"));
        }
    }
}