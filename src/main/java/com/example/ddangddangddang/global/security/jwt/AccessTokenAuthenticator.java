package com.example.ddangddangddang.global.security.jwt;

import com.example.ddangddangddang.domain.auth.exception.AuthErrorCode;
import com.example.ddangddangddang.domain.user.entity.UserRole;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.security.AuthUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Access Token을 검증하고 Authentication을 만든다.
 * HTTP 요청(JwtAuthenticationFilter)과 WebSocket 연결(StompAuthChannelInterceptor)이 같은 규칙으로 인증하도록 한 곳에 둔다.
 */
@Component
@RequiredArgsConstructor
public class AccessTokenAuthenticator {

    private final JwtProvider jwtProvider;
    private final TokenBlacklistRepository tokenBlacklistRepository;

    /**
     * 검증에 실패하면 원인에 맞는 AuthErrorCode로 BusinessException을 던진다.
     */
    public Authentication authenticate(String token) {
        return toAuthentication(verify(token));
    }

    /**
     * authenticate와 같은 규칙으로 검증하고, 나중에 validateStillActive로 다시 확인할 수 있도록 토큰 ID와 만료 시각을 함께 돌려준다.
     */
    public AuthenticatedToken authenticateForSession(String token) {
        Claims claims = verify(token);
        return new AuthenticatedToken(toAuthentication(claims), claims.getId(), claims.getExpiration().toInstant(),
                Long.valueOf(claims.getSubject()), claims.getIssuedAt().toInstant());
    }

    /**
     * 이미 검증한 토큰이 지금도 유효한지 확인한다.
     * 연결 뒤에 만료됐거나, 로그아웃(이 토큰 또는 회원의 이전 토큰 전부)됐거나, 비밀번호가 바뀌었으면 예외를 던진다.
     */
    public void validateStillActive(AuthenticatedToken token) {
        if (!Instant.now().isBefore(token.expiresAt())) {
            throw new BusinessException(AuthErrorCode.EXPIRED_TOKEN);
        }
        validateNotRevoked(token.tokenId(), token.userId(), token.issuedAt());
    }

    // 로그아웃·비밀번호 변경으로 막힌 토큰인지. HTTP 요청과 WebSocket이 같은 규칙으로 확인한다.
    private void validateNotRevoked(String tokenId, Long userId, Instant issuedAt) {
        if (tokenBlacklistRepository.exists(tokenId) || tokenBlacklistRepository.isLoggedOutForUser(userId, issuedAt)) {
            throw new BusinessException(AuthErrorCode.LOGGED_OUT_TOKEN);
        }
        // 비밀번호를 바꾸기 전에 발급된 토큰이면 막는다. (탈취된 토큰이 만료 전까지 계속 쓰이는 것 방지)
        if (tokenBlacklistRepository.isInvalidatedForUser(userId, issuedAt)) {
            throw new BusinessException(AuthErrorCode.PASSWORD_CHANGED);
        }
    }

    private Claims verify(String token) {
        Claims claims;
        try {
            claims = jwtProvider.parseClaims(token);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(AuthErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        if (!TokenType.ACCESS.name().equals(claims.get(JwtProvider.CLAIM_TYPE, String.class))) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        validateNotRevoked(claims.getId(), Long.valueOf(claims.getSubject()), claims.getIssuedAt().toInstant());

        return claims;
    }

    private Authentication toAuthentication(Claims claims) {
        UserRole role = UserRole.valueOf(claims.get(JwtProvider.CLAIM_ROLE, String.class));
        AuthUser authUser = new AuthUser(
                Long.valueOf(claims.getSubject()),
                claims.get(JwtProvider.CLAIM_EMAIL, String.class),
                claims.get(JwtProvider.CLAIM_NICKNAME, String.class),
                role
        );

        return new UsernamePasswordAuthenticationToken(
                authUser, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }
}