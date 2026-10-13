package com.example.ddangddangddang.global.security.jwt;

import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.security.SecurityErrorResponder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

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

    private final AccessTokenAuthenticator accessTokenAuthenticator;
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
        Authentication authentication;
        try {
            authentication = accessTokenAuthenticator.authenticate(token);
        } catch (BusinessException e) {
            errorResponder.respond(response, e.getErrorCode());
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}
