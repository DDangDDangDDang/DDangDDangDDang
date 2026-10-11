package com.example.ddangddangddang.global.security.jwt;

import com.example.ddangddangddang.domain.user.entity.UserRole;
import com.example.ddangddangddang.global.security.AuthErrorCode;
import com.example.ddangddangddang.global.security.AuthUser;
import com.example.ddangddangddang.global.security.SecurityErrorResponder;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private final TokenBlacklistRepository tokenBlacklistRepository;

    /**
     * 토큰 검증을 건너뛰는 경로.
     * 특히 재발급 API는 만료된 Access Token이 헤더에 남아 있어도 통과해야 한다.
     * 그렇지 않으면 "만료 -> 재발급 요청 -> 만료라서 401"로 재발급을 영영 못 한다.
     */
    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/reissue"
    );

    private final JwtProvider jwtProvider;
    private final SecurityErrorResponder errorResponder;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return EXCLUDED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = JwtProvider.resolveBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));

        // 토큰이 없으면 익명으로 통과시킨다. 인증이 필요한 API라면 EntryPoint가 401 AUTH_001을 응답한다.
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 토큰이 있는데 잘못됐다면 permitAll API여도 401로 막는다.
        // 익명으로 통과시키면 사용자가 로그인이 풀린 줄 모르게 된다.
        Claims claims;
        try {
            claims = jwtProvider.parseClaims(token);
        } catch (ExpiredJwtException e) {
            errorResponder.respond(response, AuthErrorCode.EXPIRED_TOKEN);
            return;
        } catch (JwtException | IllegalArgumentException e) {
            errorResponder.respond(response, AuthErrorCode.INVALID_TOKEN);
            return;
        }

        if (tokenBlacklistRepository.exists(claims.getId())) {
            errorResponder.respond(response, AuthErrorCode.LOGGED_OUT_TOKEN);
            return;
        }

        if (!TokenType.ACCESS.name().equals(claims.get(JwtProvider.CLAIM_TYPE, String.class))) {
            errorResponder.respond(response, AuthErrorCode.INVALID_TOKEN);
            return;
        }

        setAuthentication(claims);
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private void setAuthentication(Claims claims) {
        UserRole role = UserRole.valueOf(claims.get(JwtProvider.CLAIM_ROLE, String.class));
        AuthUser authUser = new AuthUser(
                Long.valueOf(claims.getSubject()),
                claims.get(JwtProvider.CLAIM_EMAIL, String.class),
                claims.get(JwtProvider.CLAIM_NICKNAME, String.class),
                role
        );

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                authUser, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}