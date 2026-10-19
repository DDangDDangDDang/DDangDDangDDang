package com.example.ddangddangddang.domain.auth.service;

import com.example.ddangddangddang.domain.auth.dto.LoginRequest;
import com.example.ddangddangddang.domain.auth.dto.LoginResult;
import com.example.ddangddangddang.domain.auth.dto.SignupRequest;
import com.example.ddangddangddang.domain.auth.dto.SignupResponse;
import com.example.ddangddangddang.domain.auth.exception.AuthErrorCode;
import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository.RotateResult;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.security.jwt.JwtProvider;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationErrorCode;
import com.example.ddangddangddang.domain.auth.verification.VerificationPurpose;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import com.example.ddangddangddang.domain.point.service.PointService;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private TokenBlacklistRepository tokenBlacklistRepository;

    @Mock
    private PointService pointService;

    @Mock
    private VerificationService verificationService;

    @Nested
    @DisplayName("회원가입")
    class Signup {

        private final SignupRequest request =
                new SignupRequest("user@test.com", "Abcd1234!", "경매왕", "01012345678", "phone-token");
        @Test
        @DisplayName("성공하면 비밀번호를 암호화해서 저장하고 포인트 지갑을 만든다")
        void success() {
            given(userRepository.existsByEmail(request.email())).willReturn(false);
            given(userRepository.existsByNickname(request.nickname())).willReturn(false);
            given(verificationService.consumeToken(VerificationPurpose.SIGNUP_PHONE, "phone-token")).willReturn("01012345678");
            given(passwordEncoder.encode(request.password())).willReturn("encoded");
            given(userRepository.save(any(User.class))).willAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", 1L);
                return saved;
            });

            SignupResponse response = authService.signup(request);

            assertThat(response.email()).isEqualTo("user@test.com");
            assertThat(response.nickname()).isEqualTo("경매왕");
            verify(userRepository).save(any(User.class));
            verify(pointService).createWallet(1L);
        }

        @Test
        @DisplayName("이메일이 중복되면 예외가 발생한다")
        void duplicateEmail() {
            given(userRepository.existsByEmail(request.email())).willReturn(true);

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.DUPLICATE_EMAIL);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("닉네임이 중복되면 예외가 발생한다")
        void duplicateNickname() {
            given(userRepository.existsByEmail(request.email())).willReturn(false);
            given(userRepository.existsByNickname(request.nickname())).willReturn(true);

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.DUPLICATE_NICKNAME);
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("회원가입 휴대폰 인증")
    class SignupPhone {

        private final SignupRequest request =
                new SignupRequest("user@test.com", "Abcd1234!", "경매왕", "01012345678", "phone-token");

        @Test
        @DisplayName("휴대폰 번호가 이미 가입돼 있으면 토큰을 쓰기 전에 예외가 발생한다")
        void duplicatePhone() {
            given(userRepository.existsByPhoneNumber("01012345678")).willReturn(true);

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.DUPLICATE_PHONE_NUMBER);
            verify(verificationService, never()).consumeToken(any(), any());
        }

        @Test
        @DisplayName("토큰으로 인증된 번호와 가입 요청 번호가 다르면 예외가 발생한다")
        void tokenForOtherPhone() {
            given(verificationService.consumeToken(VerificationPurpose.SIGNUP_PHONE, "phone-token")).willReturn("01099999999");

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(VerificationErrorCode.INVALID_VERIFICATION_TOKEN);
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("로그인")
    class Login {

        private final LoginRequest request = new LoginRequest("user@test.com", "Abcd1234!");

        @Test
        @DisplayName("성공하면 토큰을 발급하고 Refresh Token을 저장한다")
        void success() {
            User user = createUser(1L);
            given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));
            given(passwordEncoder.matches(request.password(), user.getPassword())).willReturn(true);
            given(jwtProvider.createAccessToken(user)).willReturn("access-token");
            given(jwtProvider.createRefreshToken(1L)).willReturn("refresh-token");
            given(jwtProvider.getAccessTokenValidity()).willReturn(Duration.ofMinutes(30));
            given(jwtProvider.getRefreshTokenValidity()).willReturn(Duration.ofDays(14));

            LoginResult result = authService.login(request);

            assertThat(result.response().accessToken()).isEqualTo("access-token");
            assertThat(result.response().expiresIn()).isEqualTo(1800);
            assertThat(result.response().user().userId()).isEqualTo(1L);
            assertThat(result.refreshToken()).isEqualTo("refresh-token");
            verify(refreshTokenRepository).save(1L, "refresh-token", Duration.ofDays(14));
        }

        @Test
        @DisplayName("이메일이 없으면 LOGIN_FAILED 예외가 발생한다")
        void emailNotFound() {
            given(userRepository.findByEmail(request.email())).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.LOGIN_FAILED);
        }

        @Test
        @DisplayName("비밀번호가 틀리면 LOGIN_FAILED 예외가 발생하고 토큰을 저장하지 않는다")
        void wrongPassword() {
            User user = createUser(1L);
            given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));
            given(passwordEncoder.matches(request.password(), user.getPassword())).willReturn(false);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.LOGIN_FAILED);
            verify(refreshTokenRepository, never()).save(anyLong(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("토큰 재발급")
    class Reissue {

        private static final String REFRESH_TOKEN = "refresh-token";

        @Test
        @DisplayName("성공하면 새 토큰을 발급하고 저장된 Refresh Token을 원자적으로 교체한다")
        void success() {
            User user = givenNewTokens();
            given(jwtProvider.getAccessTokenValidity()).willReturn(Duration.ofMinutes(30));
            given(refreshTokenRepository.rotate(1L, REFRESH_TOKEN, "new-refresh-token", Duration.ofDays(14)))
                    .willReturn(RotateResult.ROTATED);

            LoginResult result = authService.reissue(REFRESH_TOKEN);

            assertThat(result.response().accessToken()).isEqualTo("new-access-token");
            assertThat(result.response().user().nickname()).isEqualTo(user.getNickname());
            assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
        }

        @Test
        @DisplayName("이미 교체된 Refresh Token이면(재사용 감지, 저장된 토큰은 rotate에서 삭제됨) 예외가 발생한다")
        void reusedToken() {
            givenNewTokens();
            given(refreshTokenRepository.rotate(1L, REFRESH_TOKEN, "new-refresh-token", Duration.ofDays(14)))
                    .willReturn(RotateResult.REUSED);

            assertInvalidRefreshToken(REFRESH_TOKEN);
        }

        @Test
        @DisplayName("저장된 Refresh Token이 없으면(로그아웃 상태) 예외가 발생한다")
        void notStored() {
            givenNewTokens();
            given(refreshTokenRepository.rotate(1L, REFRESH_TOKEN, "new-refresh-token", Duration.ofDays(14)))
                    .willReturn(RotateResult.NOT_FOUND);

            assertInvalidRefreshToken(REFRESH_TOKEN);
        }

        // 재발급은 저장된 토큰과 비교하기 전에 새 토큰을 먼저 만든다. (비교와 교체를 Redis에서 한 번에 하기 위해)
        private User givenNewTokens() {
            User user = createUser(1L);
            given(jwtProvider.parseClaims(REFRESH_TOKEN)).willReturn(claims("1", "REFRESH"));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(jwtProvider.createAccessToken(user)).willReturn("new-access-token");
            given(jwtProvider.createRefreshToken(1L)).willReturn("new-refresh-token");
            given(jwtProvider.getRefreshTokenValidity()).willReturn(Duration.ofDays(14));
            return user;
        }

        @Test
        @DisplayName("쿠키가 없으면 예외가 발생한다")
        void noToken() {
            assertInvalidRefreshToken(null);
        }

        @Test
        @DisplayName("만료된 Refresh Token이면 예외가 발생한다")
        void expiredToken() {
            given(jwtProvider.parseClaims(REFRESH_TOKEN)).willThrow(new ExpiredJwtException(null, null, "expired"));

            assertInvalidRefreshToken(REFRESH_TOKEN);
        }

        @Test
        @DisplayName("Access Token으로 재발급을 요청하면 예외가 발생한다")
        void accessTokenUsed() {
            given(jwtProvider.parseClaims(REFRESH_TOKEN)).willReturn(claims("1", "ACCESS"));

            assertInvalidRefreshToken(REFRESH_TOKEN);
        }

        private void assertInvalidRefreshToken(String refreshToken) {
            assertThatThrownBy(() -> authService.reissue(refreshToken))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
            verify(refreshTokenRepository, never()).save(anyLong(), anyString(), any());
        }

        private Claims claims(String subject, String type) {
            return Jwts.claims().subject(subject).add(JwtProvider.CLAIM_TYPE, type).build();
        }
    }

    @Nested
    @DisplayName("로그아웃")
    class Logout {

        @Test
        @DisplayName("Refresh Token을 삭제하고 Access Token을 남은 유효시간 동안 블랙리스트에 올린다")
        void success() {
            Date expiration = Date.from(Instant.now().plus(Duration.ofMinutes(10)));
            Claims claims = Jwts.claims().id("jti-1").subject("1").expiration(expiration).build();
            given(jwtProvider.parseClaims("access-token")).willReturn(claims);

            authService.logout(1L, "access-token");

            verify(refreshTokenRepository).deleteByUserId(1L);
            verify(tokenBlacklistRepository).save(eq("jti-1"), argThat(ttl ->
                    ttl.compareTo(Duration.ofMinutes(9)) > 0 && ttl.compareTo(Duration.ofMinutes(10)) <= 0));
        }
    }

    private User createUser(Long id) {
        User user = User.create("user@test.com", "encoded", "경매왕");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}