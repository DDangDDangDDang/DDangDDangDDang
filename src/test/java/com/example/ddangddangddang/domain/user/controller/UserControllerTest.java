package com.example.ddangddangddang.domain.user.controller;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.global.security.jwt.JwtProvider;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @MockitoBean
    private VerificationService verificationService;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        SignupFixture.stubPhoneVerification(verificationService);
        accessToken = signupAndLogin("user@test.com", "경매왕");
    }

    @Nested
    @DisplayName("GET /api/v1/users/me")
    class GetMyInfo {

        @Test
        @DisplayName("로그인한 회원의 정보를 응답한다")
        void success() throws Exception {
            mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.email").value("user@test.com"))
                    .andExpect(jsonPath("$.data.nickname").value("경매왕"))
                    .andExpect(jsonPath("$.data.phoneNumber").value(SignupFixture.phoneOf("user@test.com")))
                    .andExpect(jsonPath("$.data.role").value("USER"));
        }

        @Test
        @DisplayName("토큰 없이 요청하면 401 AUTH_001")
        void withoutToken() throws Exception {
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("AUTH_001"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/users/me")
    class UpdateMyInfo {

        @Test
        @DisplayName("닉네임을 바꾸면 새 Access Token을 주고, 그 토큰에 바뀐 닉네임이 들어 있다")
        void success() throws Exception {
            String response = update(accessToken, "새닉네임")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.user.nickname").value("새닉네임"))
                    .andExpect(jsonPath("$.data.expiresIn").value(1800))
                    .andReturn().getResponse().getContentAsString();

            String newAccessToken = JsonPath.read(response, "$.data.accessToken");
            mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(newAccessToken)))
                    .andExpect(jsonPath("$.data.nickname").value("새닉네임"));
            assertThat(nicknameClaim(newAccessToken)).isEqualTo("새닉네임");
        }

        @Test
        @DisplayName("닉네임을 바꾸면 30일 뒤까지 다시 바꿀 수 없다: 429 USER_005, 내 정보에 다음 변경 가능 시각")
        void tooSoon() throws Exception {
            mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                    .andExpect(jsonPath("$.data.nicknameChangeAvailableAt").doesNotExist());
            String response = update(accessToken, "새닉네임")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.user.nicknameChangeAvailableAt").isNotEmpty())
                    .andReturn().getResponse().getContentAsString();
            String newAccessToken = JsonPath.read(response, "$.data.accessToken");

            update(newAccessToken, "또새닉네임")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.error.code").value("USER_005"));
            mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(newAccessToken)))
                    .andExpect(jsonPath("$.data.nickname").value("새닉네임"))
                    .andExpect(jsonPath("$.data.nicknameChangeAvailableAt").isNotEmpty());
        }

        @Test
        @DisplayName("다른 회원이 쓰는 닉네임이면 409 USER_002")
        void duplicate() throws Exception {
            signupAndLogin("other@test.com", "남의닉네임");

            update(accessToken, "남의닉네임")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("USER_002"));
        }

        @Test
        @DisplayName("닉네임이 2~10자가 아니면 400 COMMON_001")
        void invalidLength() throws Exception {
            update(accessToken, "가")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_001"));
        }
    }

    private ResultActions update(String token, String nickname) throws Exception {
        return mockMvc.perform(patch("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nickname": "%s"}
                        """.formatted(nickname)));
    }

    private String signupAndLogin(String email, String nickname) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SignupFixture.signupBody(email, "Abcd1234!", nickname)))
                .andExpect(status().isCreated());
        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "Abcd1234!"}
                                """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(login, "$.data.accessToken");
    }

    private String nicknameClaim(String token) {
        return jwtProvider.parseClaims(token).get(JwtProvider.CLAIM_NICKNAME, String.class);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}