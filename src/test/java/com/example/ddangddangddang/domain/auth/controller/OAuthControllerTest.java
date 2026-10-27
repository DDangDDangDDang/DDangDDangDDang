package com.example.ddangddangddang.domain.auth.controller;

import com.example.ddangddangddang.domain.auth.oauth.OAuthProviderClient;
import com.example.ddangddangddang.domain.auth.oauth.OAuthProviderClients;
import com.example.ddangddangddang.domain.auth.oauth.SocialUser;
import com.example.ddangddangddang.domain.auth.oauth.OAuthErrorCode;
import com.example.ddangddangddang.domain.auth.oauth.PendingSocialSignup;
import com.example.ddangddangddang.domain.auth.oauth.PendingSocialSignupRepository;
import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationRateLimitRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.point.repository.PointWalletRepository;
import com.example.ddangddangddang.domain.user.entity.SocialAccount;
import com.example.ddangddangddang.domain.user.entity.SocialProvider;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.SocialAccountRepository;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import com.example.ddangddangddang.support.SignupFixture;
import java.util.Optional;
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
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OAuthControllerTest {

    private static final String KAKAO_ID = "1234567890";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private PointWalletRepository pointWalletRepository;

    // 실제 카카오·구글·Redis 없이 실행하기 위해 Mock으로 대체
    @MockitoBean
    private OAuthProviderClients providerClients;

    private final OAuthProviderClient kakaoOAuthClient = mock(OAuthProviderClient.class);
    private final OAuthProviderClient googleOAuthClient = mock(OAuthProviderClient.class);

    @MockitoBean
    private PendingSocialSignupRepository pendingSocialSignupRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @MockitoBean
    private VerificationService verificationService;

    // 인증번호 요청 횟수(Redis). 기본값 0이라 IP 제한에 걸리지 않는다.
    @MockitoBean
    private VerificationRateLimitRepository verificationRateLimitRepository;

    @BeforeEach
    void stubPhoneVerification() {
        SignupFixture.stubPhoneVerification(verificationService);
        given(providerClients.get(SocialProvider.KAKAO)).willReturn(kakaoOAuthClient);
        given(providerClients.get(SocialProvider.GOOGLE)).willReturn(googleOAuthClient);
    }

    @Nested
    @DisplayName("POST /api/v1/auth/oauth/kakao")
    class Login {

        @Test
        @DisplayName("카카오 계정이 연결된 회원이면 토큰 없이 호출해도 로그인되고 Refresh Token 쿠키를 내려준다")
        void linkedUser() throws Exception {
            User user = userRepository.save(User.createSocial("user@test.com", "경매왕", "01012345678"));
            socialAccountRepository.save(SocialAccount.link(user.getId(), SocialProvider.KAKAO, KAKAO_ID));
            given(kakaoOAuthClient.getUser("auth-code")).willReturn(new SocialUser(KAKAO_ID, null, null));

            mockMvc.perform(post("/api/v1/auth/oauth/kakao")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\": \"auth-code\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("LOGIN"))
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.user.userId").value(user.getId()))
                    .andExpect(jsonPath("$.data.signupToken").doesNotExist())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")));
        }

        @Test
        @DisplayName("처음 온 사용자면 가입 대기 토큰과 카카오 이메일·닉네임을 주고, 쿠키는 내려주지 않는다")
        void newUser() throws Exception {
            given(kakaoOAuthClient.getUser("auth-code")).willReturn(new SocialUser(KAKAO_ID, "user@kakao.com", "카카오닉"));
            given(pendingSocialSignupRepository.save(any(), any())).willReturn("signup-token");

            mockMvc.perform(post("/api/v1/auth/oauth/kakao")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\": \"auth-code\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("SIGNUP_REQUIRED"))
                    .andExpect(jsonPath("$.data.signupToken").value("signup-token"))
                    .andExpect(jsonPath("$.data.email").value("user@kakao.com"))
                    .andExpect(jsonPath("$.data.nickname").value("카카오닉"))
                    .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        }

        @Test
        @DisplayName("인가 코드가 없으면 400")
        void blankCode() throws Exception {
            mockMvc.perform(post("/api/v1/auth/oauth/kakao")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\": \"\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("카카오 로그인이 설정되지 않았으면 503 OAUTH_005")
        void notConfigured() throws Exception {
            given(kakaoOAuthClient.getUser("auth-code")).willThrow(new BusinessException(OAuthErrorCode.NOT_CONFIGURED));

            mockMvc.perform(post("/api/v1/auth/oauth/kakao")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\": \"auth-code\"}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error.code").value("OAUTH_005"));
        }
    }

    @Nested
    @DisplayName("구글 / 지원하지 않는 제공자")
    class Providers {

        @Test
        @DisplayName("구글로 처음 온 사용자는 POST /oauth/google 로 가입 대기 토큰과 구글 이메일·이름을 받는다")
        void googleNewUser() throws Exception {
            given(googleOAuthClient.getUser("google-code")).willReturn(new SocialUser("google-sub", "user@gmail.com", "Google User"));
            given(pendingSocialSignupRepository.save(any(), any())).willReturn("google-signup-token");

            mockMvc.perform(post("/api/v1/auth/oauth/google")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\": \"google-code\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("SIGNUP_REQUIRED"))
                    .andExpect(jsonPath("$.data.signupToken").value("google-signup-token"))
                    .andExpect(jsonPath("$.data.email").value("user@gmail.com"));
        }

        @Test
        @DisplayName("구글 계정이 연결된 회원이면 로그인된다")
        void googleLinkedUser() throws Exception {
            User user = userRepository.save(User.createSocial("g@test.com", "구글회원", "01077778888"));
            socialAccountRepository.save(SocialAccount.link(user.getId(), SocialProvider.GOOGLE, "google-sub"));
            given(googleOAuthClient.getUser("google-code")).willReturn(new SocialUser("google-sub", "g@test.com", null));

            mockMvc.perform(post("/api/v1/auth/oauth/google")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\": \"google-code\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("LOGIN"))
                    .andExpect(jsonPath("$.data.user.userId").value(user.getId()));
        }

        @Test
        @DisplayName("지원하지 않는 제공자면 400 OAUTH_006")
        void unsupportedProvider() throws Exception {
            mockMvc.perform(post("/api/v1/auth/oauth/naver")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\": \"code\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("OAUTH_006"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/oauth/kakao/phone/code")
    class PhoneCode {

        @Test
        @DisplayName("이미 가입된 번호에도 인증번호를 보낸다 (일반 가입은 USER_004)")
        void registeredPhone() throws Exception {
            userRepository.save(User.create("old@test.com", "encoded", "기존회원", "01055556666"));
            given(pendingSocialSignupRepository.find("signup-token")).willReturn(Optional.of(
                    new PendingSocialSignup(SocialProvider.KAKAO, KAKAO_ID, null, null)));

            mockMvc.perform(post("/api/v1/auth/oauth/kakao/phone/code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"signupToken\": \"signup-token\", \"phoneNumber\": \"01055556666\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.expiresIn").value(300));
        }

        @Test
        @DisplayName("가입 대기 토큰이 없으면 400 OAUTH_003")
        void expiredSignupToken() throws Exception {
            given(pendingSocialSignupRepository.find("expired")).willReturn(Optional.empty());

            mockMvc.perform(post("/api/v1/auth/oauth/kakao/phone/code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"signupToken\": \"expired\", \"phoneNumber\": \"01055556666\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("OAUTH_003"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/oauth/kakao/signup")
    class Signup {

        private static final String PHONE = "01055556666";

        @BeforeEach
        void pendingSignup() {
            given(pendingSocialSignupRepository.find("signup-token")).willReturn(Optional.of(
                    new PendingSocialSignup(SocialProvider.KAKAO, KAKAO_ID, "user@kakao.com", "카카오닉")));
        }

        @Test
        @DisplayName("새 회원을 만들고(비밀번호 없음, 지갑, 카카오 연결) 로그인한다")
        void createUser() throws Exception {
            mockMvc.perform(post("/api/v1/auth/oauth/kakao/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(signupBody("new@test.com", "새회원", PHONE)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.linkedExistingAccount").value(false))
                    .andExpect(jsonPath("$.data.user.nickname").value("새회원"))
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")));

            User user = userRepository.findByEmail("new@test.com").orElseThrow();
            assertThat(user.hasPassword()).isFalse();
            assertThat(pointWalletRepository.findByUserId(user.getId())).isPresent();
            assertThat(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, KAKAO_ID))
                    .get().extracting(SocialAccount::getUserId).isEqualTo(user.getId());
            verify(pendingSocialSignupRepository).delete("signup-token");
        }

        @Test
        @DisplayName("인증한 번호로 가입한 회원이 있으면 그 계정에 카카오를 연결하고 로그인한다")
        void linkToExistingUser() throws Exception {
            User existing = userRepository.save(User.create("old@test.com", "encoded", "기존회원", PHONE));

            mockMvc.perform(post("/api/v1/auth/oauth/kakao/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(signupBody("ignored@test.com", "무시됨", PHONE)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.linkedExistingAccount").value(true))
                    .andExpect(jsonPath("$.data.user.userId").value(existing.getId()))
                    .andExpect(jsonPath("$.data.user.nickname").value("기존회원"));

            assertThat(userRepository.findByEmail("ignored@test.com")).isEmpty();
            assertThat(socialAccountRepository.existsByUserIdAndProvider(existing.getId(), SocialProvider.KAKAO)).isTrue();
        }

        @Test
        @DisplayName("다른 번호로 인증한 토큰이면 400 VERIFY_004, 회원을 만들지 않는다")
        void otherPhoneToken() throws Exception {
            mockMvc.perform(post("/api/v1/auth/oauth/kakao/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"signupToken": "signup-token", "nickname": "새회원", "email": "new@test.com",
                                     "phoneNumber": "%s", "phoneVerificationToken": "%s"}
                                    """.formatted(PHONE, SignupFixture.tokenOf("01099999999"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VERIFY_004"));

            assertThat(userRepository.findByEmail("new@test.com")).isEmpty();
        }

        @Test
        @DisplayName("닉네임 형식이 틀리면 400")
        void invalidNickname() throws Exception {
            mockMvc.perform(post("/api/v1/auth/oauth/kakao/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(signupBody("new@test.com", "a", PHONE)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("가입 대기 토큰이 만료됐으면 400 OAUTH_003")
        void expiredSignupToken() throws Exception {
            given(pendingSocialSignupRepository.find("signup-token")).willReturn(Optional.empty());

            mockMvc.perform(post("/api/v1/auth/oauth/kakao/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(signupBody("new@test.com", "새회원", PHONE)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("OAUTH_003"));
        }

        private String signupBody(String email, String nickname, String phone) {
            return """
                    {"signupToken": "signup-token", "nickname": "%s", "email": "%s",
                     "phoneNumber": "%s", "phoneVerificationToken": "%s"}
                    """.formatted(nickname, email, phone, SignupFixture.tokenOf(phone));
        }
    }
}
