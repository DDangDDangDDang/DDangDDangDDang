package com.example.ddangddangddang.domain.auth.controller;

import com.example.ddangddangddang.domain.auth.exception.AuthErrorCode;
import com.example.ddangddangddang.domain.auth.repository.LoginAttemptRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 로그인 API를 IP당 5분에 30회로 제한한다.
 *
 * 이메일별 실패 제한(AuthService.login)만 있으면, 이메일을 바꿔가며 시도해 비밀번호를 대입하거나
 * BCrypt 비교로 CPU를 계속 쓰게 만들 수 있다. 학교·회사처럼 여러 사람이 IP 하나를 같이 쓰는 경우를 생각해 넉넉히 잡는다.
 *
 * IP는 request.getRemoteAddr()로 얻는다. (VerificationIpRateLimitInterceptor와 같은 이유로 헤더를 직접 읽지 않는다)
 */
@Component
@RequiredArgsConstructor
public class LoginIpRateLimitInterceptor implements HandlerInterceptor {

    public static final int MAX_REQUESTS_PER_IP = 30;
    public static final Duration WINDOW = Duration.ofMinutes(5);

    private final LoginAttemptRepository loginAttemptRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        long count = loginAttemptRepository.increaseIpCount(request.getRemoteAddr(), WINDOW);
        if (count > MAX_REQUESTS_PER_IP) {
            throw new BusinessException(AuthErrorCode.TOO_MANY_LOGIN_REQUESTS);
        }
        return true;
    }
}
