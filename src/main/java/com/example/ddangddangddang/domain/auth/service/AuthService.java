package com.example.ddangddangddang.domain.auth.service;

import com.example.ddangddangddang.domain.auth.dto.*;
import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.security.AuthErrorCode;
import com.example.ddangddangddang.global.security.jwt.JwtProvider;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
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

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistRepository tokenBlacklistRepository;


    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_NICKNAME);
        }

        User user = User.create(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.nickname()
        );
        User savedUser = userRepository.save(user);

        return SignupResponse.from(savedUser);
    }

    public LoginResult login(LoginRequest request) {
        // 이메일이 없을 때와 비밀번호가 틀릴 때 같은 에러를 던져 계정 존재 여부를 노출하지 않는다
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.LOGIN_FAILED));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
        }

        return issueTokens(user);
    }

    private LoginResult issueTokens(User user) {
        String accessToken = jwtProvider.createAccessToken(user);
        String refreshToken = jwtProvider.createRefreshToken(user.getId());
        refreshTokenRepository.save(user.getId(), refreshToken, jwtProvider.getRefreshTokenValidity());

        long expiresIn = jwtProvider.getAccessTokenValidity().toSeconds();
        return new LoginResult(LoginResponse.of(accessToken, expiresIn, user), refreshToken);
    }

    /**
     * Refresh Token Rotation: 재발급할 때마다 Refresh Token도 새로 발급하고 이전 것은 폐기한다.
     * 이미 교체된(폐기된) Refresh Token으로 요청이 오면 탈취로 보고 저장된 토큰까지 삭제해 강제 로그아웃시킨다.
     */
    public LoginResult reissue(String refreshToken) {
        Long userId = extractUserId(refreshToken);

        String savedToken = refreshTokenRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        if (!savedToken.equals(refreshToken)) {
            refreshTokenRepository.deleteByUserId(userId);
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        return issueTokens(user);
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