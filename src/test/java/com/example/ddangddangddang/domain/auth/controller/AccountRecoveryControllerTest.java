package com.example.ddangddangddang.domain.auth.controller;

import com.example.ddangddangddang.domain.auth.repository.LoginAttemptRepository;
import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationPurpose;
import com.example.ddangddangddang.domain.auth.verification.VerificationRateLimitRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.global.message.EmailSender;
import com.example.ddangddangddang.global.message.SmsSender;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import com.example.ddangddangddang.support.SignupFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountRecoveryControllerTest {

    private static final String EMAIL = "user@test.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    // 로그인 시도 횟수(Redis). 기본값 0이라 제한에 걸리지 않는다.
    @MockitoBean
    private LoginAttemptRepository loginAttemptRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @MockitoBean
    private VerificationService verificationService;

    @MockitoBean
    private SmsSender smsSender;

    @MockitoBean
    private EmailSender emailSender;

    // 인증번호 요청 횟수(Redis). 기본값 0이라 제한에 걸리지 않는다.
    @MockitoBean
    private VerificationRateLimitRepository verificationRateLimitRepository;

    @BeforeEach
    void setUp() throws Exception {
        SignupFixture.stubPhoneVerification(verificationService);
        request("/api/v1/auth/signup", SignupFixture.signupBody(EMAIL, "Abcd1234!", "경매왕"))
                .andExpect(status().isCreated());
    }

    @Nested
    @DisplayName("이메일 찾기")
    class FindEmail {

        @Test
        @DisplayName("휴대폰 인증에 성공하면 가린 이메일을 응답한다")
        void success() throws Exception {
            String phone = SignupFixture.phoneOf(EMAIL);
            given(verificationService.verifyCode(VerificationPurpose.FIND_EMAIL, phone, "123456")).willReturn("token");

            request("/api/v1/auth/find-email/verify", """
                    {"phoneNumber": "%s", "code": "123456"}
                    """.formatted(phone))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.maskedEmail").value("us**@test.com"))
                    .andExpect(jsonPath("$.data.joinedAt").isNotEmpty());
        }

        @Test
        @DisplayName("가입되지 않은 번호면 404 USER_003")
        void notRegistered() throws Exception {
            given(verificationService.verifyCode(VerificationPurpose.FIND_EMAIL, "01099999999", "123456")).willReturn("token");

            request("/api/v1/auth/find-email/verify", """
                    {"phoneNumber": "01099999999", "code": "123456"}
                    """)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("USER_003"));
        }
    }

    @Nested
    @DisplayName("비밀번호 재설정")
    class ResetPassword {

        @Test
        @DisplayName("가입 여부와 상관없이 같은 응답을 주고, 가입된 이메일에만 메일을 보낸다")
        void sameResponseForUnknownEmail() throws Exception {
            given(verificationService.issueCode(eq(VerificationPurpose.RESET_PASSWORD), anyString())).willReturn("123456");

            request("/api/v1/auth/password-reset/code", """
                    {"email": "nobody@test.com"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.expiresIn").value(300));
            verify(emailSender, never()).send(anyString(), anyString(), anyString());

            request("/api/v1/auth/password-reset/code", """
                    {"email": "%s"}
                    """.formatted(EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.expiresIn").value(300));
            verify(emailSender).send(eq(EMAIL), anyString(), anyString());
        }

        @Test
        @DisplayName("재설정하면 새 비밀번호로만 로그인되고 Refresh Token이 삭제된다")
        void resetAndLogin() throws Exception {
            given(verificationService.consumeToken(VerificationPurpose.RESET_PASSWORD, "reset-token")).willReturn(EMAIL);

            request("/api/v1/auth/password-reset", """
                    {"verificationToken": "reset-token", "newPassword": "NewPass123!"}
                    """)
                    .andExpect(status().isOk());

            verify(refreshTokenRepository).deleteByUserId(any());
            verify(tokenBlacklistRepository).invalidateTokensIssuedBefore(any(), any(), any());
            login("Abcd1234!").andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("AUTH_008"));
            login("NewPass123!").andExpect(status().isOk());
        }

        @Test
        @DisplayName("새 비밀번호가 규칙에 맞지 않으면 400")
        void weakPassword() throws Exception {
            request("/api/v1/auth/password-reset", """
                    {"verificationToken": "reset-token", "newPassword": "12345678"}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_001"));
        }

        private ResultActions login(String password) throws Exception {
            return request("/api/v1/auth/login", """
                    {"email": "%s", "password": "%s"}
                    """.formatted(EMAIL, password));
        }
    }

    private ResultActions request(String url, String body) throws Exception {
        return mockMvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}