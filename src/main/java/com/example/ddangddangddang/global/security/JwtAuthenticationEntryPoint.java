package com.example.ddangddangddang.global.security;

import com.example.ddangddangddang.domain.auth.exception.AuthErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증이 필요한 API에 토큰 없이 접근하면 401 AUTH_001을 응답한다.
 * 토큰이 있는데 잘못된 경우는 JwtAuthenticationFilter에서 먼저 응답한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorResponder errorResponder;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        errorResponder.respond(response, AuthErrorCode.TOKEN_NOT_FOUND);
    }
}