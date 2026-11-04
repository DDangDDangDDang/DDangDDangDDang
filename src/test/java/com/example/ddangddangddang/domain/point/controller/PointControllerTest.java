package com.example.ddangddangddang.domain.point.controller;

import com.example.ddangddangddang.domain.auth.repository.LoginAttemptRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
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

    // 로그인 시도 횟수(Redis). 기본값 0이라 제한에 걸리지 않는다.
    @MockitoBean
    private LoginAttemptRepository loginAttemptRepository;

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

    @Nested
    @DisplayName("POST /api/v1/points/exchanges")
    class RequestExchange {

        private static final String EXCHANGE_BODY = """
                {"amount": 10000, "bankCode": "088", "accountNumber": "110-123-456789", "accountHolder": "경매왕"}
                """;

        @Test
        @DisplayName("충전한 포인트만 있으면 환전할 수 없다 (POINT_047)")
        void chargedOnly() throws Exception {
            PointWallet wallet = pointWalletRepository.findByUserId(userId).orElseThrow();
            wallet.charge(50_000L);
            pointWalletRepository.saveAndFlush(wallet);

            mockMvc.perform(get("/api/v1/points/wallet")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                    .andExpect(jsonPath("$.data.balance").value(50_000))
                    .andExpect(jsonPath("$.data.exchangeableBalance").value(0));

            mockMvc.perform(post("/api/v1/points/exchanges")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(EXCHANGE_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("POINT_047"));
        }

        @Test
        @DisplayName("정산받은 포인트는 환전할 수 있고, 계좌번호는 DB에 암호화돼 저장된다")
        void earnedAndEncrypted() throws Exception {
            PointWallet wallet = pointWalletRepository.findByUserId(userId).orElseThrow();
            wallet.settle(10_000L);
            pointWalletRepository.saveAndFlush(wallet);

            String response = mockMvc.perform(post("/api/v1/points/exchanges")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(EXCHANGE_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.accountNumber").value("110******789"))
                    .andReturn().getResponse().getContentAsString();
            long exchangeId = ((Number) JsonPath.read(response, "$.data.exchangeId")).longValue();

            String stored = jdbcTemplate.queryForObject(
                    "select account_number from point_exchanges where id = ?", String.class, exchangeId);
            assertThat(stored).startsWith("enc:v1:").doesNotContain("110123456789");
        }

        @Test
        @DisplayName("암호화 전에 평문으로 저장된 계좌번호는 서버 시작 작업이 암호화하고, 원래 값으로 읽힌다")
        void legacyPlaintextIsReencrypted() throws Exception {
            PointWallet wallet = pointWalletRepository.findByUserId(userId).orElseThrow();
            wallet.settle(10_000L);
            pointWalletRepository.saveAndFlush(wallet);
            String response = mockMvc.perform(post("/api/v1/points/exchanges")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(EXCHANGE_BODY))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            long exchangeId = ((Number) JsonPath.read(response, "$.data.exchangeId")).longValue();
            jdbcTemplate.update("update point_exchanges set account_number = ? where id = ?", "110123456789", exchangeId);

            new com.example.ddangddangddang.domain.point.service.AccountNumberReencryptRunner(jdbcTemplate, accountNumberConverter)
                    .run(null);

            String stored = jdbcTemplate.queryForObject(
                    "select account_number from point_exchanges where id = ?", String.class, exchangeId);
            assertThat(stored).startsWith("enc:v1:");
            assertThat(accountNumberConverter.convertToEntityAttribute(stored)).isEqualTo("110123456789");
        }
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private com.example.ddangddangddang.domain.point.entity.AccountNumberConverter accountNumberConverter;
}