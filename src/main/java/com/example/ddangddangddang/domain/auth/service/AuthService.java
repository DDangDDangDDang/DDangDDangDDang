package com.example.ddangddangddang.domain.auth.service;

import com.example.ddangddangddang.domain.auth.dto.*;
import com.example.ddangddangddang.domain.auth.repository.LoginAttemptRepository;
import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository.RotateResult;
import com.example.ddangddangddang.domain.point.service.PointService;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.domain.auth.exception.AuthErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.security.jwt.JwtProvider;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationErrorCode;
import com.example.ddangddangddang.domain.auth.verification.VerificationPurpose;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.ddangddangddang.global.security.jwt.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    public static final int MAX_LOGIN_FAILURES = 5;
    public static final Duration LOGIN_FAILURE_WINDOW = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final PointService pointService;
    private final VerificationService verificationService;
    private final LoginAttemptRepository loginAttemptRepository;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_NICKNAME);
        }
        if (userRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_PHONE_NUMBER);
        }
        // 중복 검사를 먼저 해야 이메일 중복 같은 실패로 인증 토큰을 헛되이 쓰지 않는다
        String verifiedPhoneNumber = verificationService.consumeToken(
                VerificationPurpose.SIGNUP_PHONE, request.phoneVerificationToken());
        if (!verifiedPhoneNumber.equals(request.phoneNumber())) {
            throw new BusinessException(VerificationErrorCode.INVALID_VERIFICATION_TOKEN);
        }

        User user = User.create(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.nickname(),
                request.phoneNumber()
        );

        User savedUser = userRepository.save(user);
        pointService.createWallet(savedUser.getId());

        return SignupResponse.from(savedUser);
    }

    /**
     * 이메일별로 15분 안에 5번 틀리면, 첫 실패부터 15분이 지날 때까지 비밀번호를 비교하지 않고 막는다.
     * 없는 이메일도 똑같이 세어 계정 존재 여부를 노출하지 않는다. (IP별 제한은 LoginIpRateLimitInterceptor)
     *
     * 비밀번호를 비교하기 전에 시도 횟수를 먼저 올리고(실패로 가정), 성공하면 지운다.
     * 횟수를 읽고 나서 실패 때 올리면, 같은 이메일로 동시에 들어온 요청이 모두 "5회 미만"을 읽고 제한을 넘어 시도할 수 있다.
     */
    public LoginResult login(LoginRequest request) {
        if (loginAttemptRepository.increaseFailureCount(request.email(), LOGIN_FAILURE_WINDOW) > MAX_LOGIN_FAILURES) {
            throw new BusinessException(AuthErrorCode.TOO_MANY_LOGIN_FAILURES);
        }

        // 이메일이 없을 때와 비밀번호가 틀릴 때 같은 에러를 던져 계정 존재 여부를 노출하지 않는다
        // 소셜 로그인으로만 가입해 비밀번호가 없는 회원도 같은 에러로 응답
        User user = userRepository.findByEmail(request.email())
                .filter(found -> found.hasPassword() && passwordEncoder.matches(request.password(), found.getPassword()))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.LOGIN_FAILED));

        loginAttemptRepository.clearFailures(request.email());
        return issueTokens(user);
    }

    // 로그인 처리(토큰 발급, Refresh Token 저장). 소셜 로그인(OAuthService)도 같은 방식으로 로그인시킨다.
    LoginResult issueTokens(User user) {
        String accessToken = jwtProvider.createAccessToken(user);
        String refreshToken = jwtProvider.createRefreshToken(user.getId());
        refreshTokenRepository.save(user.getId(), refreshToken, jwtProvider.getRefreshTokenValidity());

        return toLoginResult(user, accessToken, refreshToken);
    }

    private LoginResult toLoginResult(User user, String accessToken, String refreshToken) {
        long expiresIn = jwtProvider.getAccessTokenValidity().toSeconds();
        return new LoginResult(LoginResponse.of(accessToken, expiresIn, user), refreshToken);
    }

    /**
     * Refresh Token Rotation: 재발급할 때마다 Refresh Token도 새로 발급하고 이전 것은 폐기한다.
     * 이미 교체된(폐기된) Refresh Token으로 요청이 오면 탈취로 보고 저장된 토큰까지 삭제해 강제 로그아웃시킨다.
     * <p>
     * 저장된 토큰 확인과 교체는 Redis에서 한 번에 실행한다(rotate).
     * 따로 하면 "확인 → (로그아웃이 토큰 삭제) → 새 토큰 저장" 순서로 겹쳐 로그아웃 뒤에도 새 토큰이 살아남는다.
     */
    public LoginResult reissue(String refreshToken) {
        Long userId = extractUserId(refreshToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        String accessToken = jwtProvider.createAccessToken(user);
        String newRefreshToken = jwtProvider.createRefreshToken(userId);

        RotateResult result = refreshTokenRepository.rotate(
                userId, refreshToken, newRefreshToken, jwtProvider.getRefreshTokenValidity());
        if (result != RotateResult.ROTATED) {
            // NOT_FOUND: 로그아웃·만료, REUSED: 재사용 감지(저장된 토큰은 rotate에서 이미 삭제됨)
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        return toLoginResult(user, accessToken, newRefreshToken);
    }

    private Long extractUserId(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        try {
            Claims claims = jwtProvider.parseClaims(refreshToken);
            if (!TokenType.REFRESH.name().equals(claims.get(JwtProvider.CLAIM_TYPE, String.class))) {
                throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
            }
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);

        }
    }

    /**
     * Refresh Token을 삭제해 재발급을 막고, 현재 Access Token은 남은 유효시간 동안 블랙리스트에 올린다.
     * accessToken은 인증 필터를 이미 통과한 토큰이다.
     */
    public void logout(Long userId, String accessToken) {
        refreshTokenRepository.deleteByUserId(userId);

        Claims claims = jwtProvider.parseClaims(accessToken);
        Duration remaining = Duration.between(Instant.now(), claims.getExpiration().toInstant());
        if (!remaining.isNegative() && !remaining.isZero()) {
            tokenBlacklistRepository.save(claims.getId(), remaining);
        }
    }
}