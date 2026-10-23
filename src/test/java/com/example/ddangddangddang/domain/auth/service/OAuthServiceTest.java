package com.example.ddangddangddang.domain.auth.service;

import com.example.ddangddangddang.domain.auth.dto.KakaoLoginResponse;
import com.example.ddangddangddang.domain.auth.dto.KakaoLoginResult;
import com.example.ddangddangddang.domain.auth.dto.KakaoSignupRequest;
import com.example.ddangddangddang.domain.auth.dto.KakaoSignupResult;
import com.example.ddangddangddang.domain.auth.dto.LoginResponse;
import com.example.ddangddangddang.domain.auth.dto.LoginResult;
import com.example.ddangddangddang.domain.auth.oauth.KakaoOAuthClient;
import com.example.ddangddangddang.domain.auth.oauth.KakaoUser;
import com.example.ddangddangddang.domain.auth.oauth.OAuthErrorCode;
import com.example.ddangddangddang.domain.auth.oauth.PendingSocialSignup;
import com.example.ddangddangddang.domain.auth.oauth.PendingSocialSignupRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationErrorCode;
import com.example.ddangddangddang.domain.auth.verification.VerificationPurpose;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.point.service.PointService;
import com.example.ddangddangddang.domain.user.entity.SocialAccount;
import com.example.ddangddangddang.domain.user.entity.SocialProvider;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.SocialAccountRepository;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OAuthServiceTest {

    private static final String KAKAO_ID = "1234567890";
    private static final String SIGNUP_TOKEN = "signup-token";
    private static final String PHONE = "01012345678";

    @InjectMocks
    private OAuthService oAuthService;

    @Mock
    private KakaoOAuthClient kakaoOAuthClient;

    @Mock
    private PendingSocialSignupRepository pendingSocialSignupRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VerificationService verificationService;

    @Mock
    private PointService pointService;

    @Mock
    private AuthService authService;

    @Nested
    @DisplayName("카카오 로그인")
    class Login {

        @BeforeEach
        void kakaoUser() {
            given(kakaoOAuthClient.getUser("auth-code")).willReturn(new KakaoUser(KAKAO_ID, "user@kakao.com", "카카오닉"));
        }

        @Test
        @DisplayName("카카오 계정이 연결된 회원이면 로그인한다")
        void linkedUser() {
            User user = user(7L, "user@test.com", "경매왕", PHONE);
            given(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, KAKAO_ID))
                    .willReturn(Optional.of(SocialAccount.link(7L, SocialProvider.KAKAO, KAKAO_ID)));
            given(userRepository.findById(7L)).willReturn(Optional.of(user));
            given(authService.issueTokens(user)).willReturn(loginResult(user));

            KakaoLoginResult result = oAuthService.loginWithKakao("auth-code");

            assertThat(result.loggedIn()).isTrue();
            assertThat(result.response().status()).isEqualTo(KakaoLoginResponse.Status.LOGIN);
            assertThat(result.response().user().userId()).isEqualTo(7L);
            assertThat(result.refreshToken()).isEqualTo("refresh-token");
            verify(pendingSocialSignupRepository, never()).save(any(), any());
        }

        @Test
        @DisplayName("처음 온 사용자면 가입 대기 토큰과 카카오 이메일·닉네임을 준다 (로그인하지 않음)")
        void newUser() {
            given(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, KAKAO_ID))
                    .willReturn(Optional.empty());
            given(pendingSocialSignupRepository.save(
                    new PendingSocialSignup(SocialProvider.KAKAO, KAKAO_ID, "user@kakao.com", "카카오닉"),
                    OAuthService.SIGNUP_TOKEN_TTL)).willReturn(SIGNUP_TOKEN);

            KakaoLoginResult result = oAuthService.loginWithKakao("auth-code");

            assertThat(result.loggedIn()).isFalse();
            assertThat(result.response().status()).isEqualTo(KakaoLoginResponse.Status.SIGNUP_REQUIRED);
            assertThat(result.response().signupToken()).isEqualTo(SIGNUP_TOKEN);
            assertThat(result.response().email()).isEqualTo("user@kakao.com");
            assertThat(result.response().nickname()).isEqualTo("카카오닉");
            verify(authService, never()).issueTokens(any());
        }
    }

    @Nested
    @DisplayName("카카오 가입")
    class Signup {

        private final KakaoSignupRequest request =
                new KakaoSignupRequest(SIGNUP_TOKEN, "경매왕", "user@test.com", PHONE, "phone-token");
        private final PendingSocialSignup pending =
                new PendingSocialSignup(SocialProvider.KAKAO, KAKAO_ID, "user@kakao.com", "카카오닉");

        @BeforeEach
        void pendingSignup() {
            given(pendingSocialSignupRepository.find(SIGNUP_TOKEN)).willReturn(Optional.of(pending));
        }

        @Test
        @DisplayName("새 회원이면 비밀번호 없는 회원, 포인트 지갑, 카카오 연결을 만들고 로그인한다")
        void createUser() {
            noLinkedKakao();
            given(userRepository.findByPhoneNumber(PHONE)).willReturn(Optional.empty());
            given(verificationService.consumeToken(VerificationPurpose.SIGNUP_PHONE, "phone-token")).willReturn(PHONE);
            given(userRepository.save(any(User.class))).willAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", 10L);
                return saved;
            });
            given(authService.issueTokens(any(User.class))).willAnswer(invocation -> loginResult(invocation.getArgument(0)));

            KakaoSignupResult result = oAuthService.signupWithKakao(request);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getEmail()).isEqualTo("user@test.com");
            assertThat(userCaptor.getValue().getNickname()).isEqualTo("경매왕");
            assertThat(userCaptor.getValue().getPhoneNumber()).isEqualTo(PHONE);
            assertThat(userCaptor.getValue().hasPassword()).isFalse();
            verify(pointService).createWallet(10L);
            verifyLinked(10L);
            verify(pendingSocialSignupRepository).delete(SIGNUP_TOKEN);
            assertThat(result.response().linkedExistingAccount()).isFalse();
            assertThat(result.response().user().userId()).isEqualTo(10L);
            assertThat(result.refreshToken()).isEqualTo("refresh-token");
        }

        @Test
        @DisplayName("인증한 번호로 가입한 회원이 있으면 새로 만들지 않고 그 계정에 카카오를 연결한다")
        void linkToExistingUser() {
            User existing = user(3L, "old@test.com", "기존회원", PHONE);
            noLinkedKakao();
            given(userRepository.findByPhoneNumber(PHONE)).willReturn(Optional.of(existing));
            given(socialAccountRepository.existsByUserIdAndProvider(3L, SocialProvider.KAKAO)).willReturn(false);
            given(verificationService.consumeToken(VerificationPurpose.SIGNUP_PHONE, "phone-token")).willReturn(PHONE);
            given(authService.issueTokens(existing)).willReturn(loginResult(existing));

            KakaoSignupResult result = oAuthService.signupWithKakao(request);

            verify(userRepository, never()).save(any());
            verify(pointService, never()).createWallet(anyLong());
            verifyLinked(3L);
            verify(pendingSocialSignupRepository).delete(SIGNUP_TOKEN);
            assertThat(result.response().linkedExistingAccount()).isTrue();
            assertThat(result.response().user().nickname()).isEqualTo("기존회원");
        }

        @Test
        @DisplayName("그 번호의 계정에 이미 다른 카카오가 연결돼 있으면 OAUTH_004, 휴대폰 인증 토큰은 쓰지 않는다")
        void existingUserHasOtherKakao() {
            noLinkedKakao();
            given(userRepository.findByPhoneNumber(PHONE)).willReturn(Optional.of(user(3L, "old@test.com", "기존회원", PHONE)));
            given(socialAccountRepository.existsByUserIdAndProvider(3L, SocialProvider.KAKAO)).willReturn(true);

            assertThatThrownBy(() -> oAuthService.signupWithKakao(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(OAuthErrorCode.ALREADY_LINKED_OTHER_ACCOUNT);
            verify(verificationService, never()).consumeToken(any(), anyString());
            verify(pendingSocialSignupRepository, never()).delete(anyString());
        }

        @Test
        @DisplayName("이메일이 중복이면 USER_001, 휴대폰 인증 토큰과 가입 대기 토큰을 쓰지 않아 다시 시도할 수 있다")
        void duplicateEmail() {
            noLinkedKakao();
            given(userRepository.findByPhoneNumber(PHONE)).willReturn(Optional.empty());
            given(userRepository.existsByEmail("user@test.com")).willReturn(true);

            assertThatThrownBy(() -> oAuthService.signupWithKakao(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.DUPLICATE_EMAIL);
            verify(verificationService, never()).consumeToken(any(), anyString());
            verify(pendingSocialSignupRepository, never()).delete(anyString());
        }

        @Test
        @DisplayName("닉네임이 중복이면 USER_002, 토큰을 쓰지 않는다")
        void duplicateNickname() {
            noLinkedKakao();
            given(userRepository.findByPhoneNumber(PHONE)).willReturn(Optional.empty());
            given(userRepository.existsByNickname("경매왕")).willReturn(true);

            assertThatThrownBy(() -> oAuthService.signupWithKakao(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.DUPLICATE_NICKNAME);
            verify(verificationService, never()).consumeToken(any(), anyString());
            verify(pendingSocialSignupRepository, never()).delete(anyString());
        }

        @Test
        @DisplayName("인증한 번호와 요청한 번호가 다르면 VERIFY_004")
        void phoneMismatch() {
            noLinkedKakao();
            given(userRepository.findByPhoneNumber(PHONE)).willReturn(Optional.empty());
            given(verificationService.consumeToken(VerificationPurpose.SIGNUP_PHONE, "phone-token")).willReturn("01099999999");

            assertThatThrownBy(() -> oAuthService.signupWithKakao(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(VerificationErrorCode.INVALID_VERIFICATION_TOKEN);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("다른 탭에서 이미 가입을 마쳤으면 그 회원으로 로그인한다")
        void alreadyLinkedInAnotherTab() {
            User user = user(5L, "user@test.com", "경매왕", PHONE);
            given(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, KAKAO_ID))
                    .willReturn(Optional.of(SocialAccount.link(5L, SocialProvider.KAKAO, KAKAO_ID)));
            given(userRepository.findById(5L)).willReturn(Optional.of(user));
            given(authService.issueTokens(user)).willReturn(loginResult(user));

            KakaoSignupResult result = oAuthService.signupWithKakao(request);

            assertThat(result.response().user().userId()).isEqualTo(5L);
            assertThat(result.response().linkedExistingAccount()).isFalse();
            verify(verificationService, never()).consumeToken(any(), anyString());
            verify(pendingSocialSignupRepository).delete(SIGNUP_TOKEN);
        }
    }

    @Test
    @DisplayName("가입 대기 토큰이 없거나 만료됐으면 OAUTH_003")
    void signupTokenExpired() {
        given(pendingSocialSignupRepository.find("expired")).willReturn(Optional.empty());

        assertThatThrownBy(() -> oAuthService.signupWithKakao(
                new KakaoSignupRequest("expired", "경매왕", "user@test.com", PHONE, "phone-token")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OAuthErrorCode.SIGNUP_TOKEN_EXPIRED);
    }

    private void noLinkedKakao() {
        given(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, KAKAO_ID))
                .willReturn(Optional.empty());
    }

    private void verifyLinked(Long userId) {
        ArgumentCaptor<SocialAccount> captor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(captor.getValue().getProviderUserId()).isEqualTo(KAKAO_ID);
    }

    private User user(Long id, String email, String nickname, String phone) {
        User user = User.create(email, "encoded", nickname, phone);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private LoginResult loginResult(User user) {
        return new LoginResult(LoginResponse.of("access-token", 1800, user), "refresh-token");
    }
}
