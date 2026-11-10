package com.example.ddangddangddang.global.security.jwt;

import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.exception.CommonErrorCode;
import com.example.ddangddangddang.global.security.SecurityErrorResponder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Slf4j
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
        } catch (DataAccessException e) {
            // 블랙리스트를 확인하는 Redis가 응답하지 않는 경우. 토큰 문제가 아니므로 401이 아니라 503으로 알린다.
            // 401을 주면 프론트가 재발급을 시도하다 로그아웃시켜, 서버 장애가 "로그인이 풀린 것"처럼 보인다.
            log.warn("토큰 블랙리스트를 확인하지 못했습니다: {}", e.getMostSpecificCause().getMessage());
            errorResponder.respond(response, CommonErrorCode.SERVICE_UNAVAILABLE);
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}
