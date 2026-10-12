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
        Claims claims;
        try {
            claims = jwtProvider.parseClaims(token);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(AuthErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        if (tokenBlacklistRepository.exists(claims.getId())) {
            throw new BusinessException(AuthErrorCode.LOGGED_OUT_TOKEN);
        }

        if (!TokenType.ACCESS.name().equals(claims.get(JwtProvider.CLAIM_TYPE, String.class))) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        return toAuthentication(claims);
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
