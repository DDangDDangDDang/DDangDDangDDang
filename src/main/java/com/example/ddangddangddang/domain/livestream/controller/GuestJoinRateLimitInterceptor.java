package com.example.ddangddangddang.domain.livestream.controller;

import com.example.ddangddangddang.domain.livestream.exception.LiveStreamErrorCode;
import com.example.ddangddangddang.domain.livestream.repository.GuestJoinRateLimitRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 손님 시청 입장(POST /live-streams/{id}/join, 토큰 없음)을 IP당 1분에 20회로 제한한다.
 * 인증 없이 반복 호출하면 요청마다 방송 조회와 LiveKit 토큰 발급이 일어나므로, 컨트롤러에 닿기 전에 막는다.
 * 로그인한 회원의 입장은 세지 않는다. (회원은 identity가 회원 번호로 고정되어 반복 접속해도 시청자 수가 늘지 않음)
 * <p>
 * IP는 request.getRemoteAddr()로 얻는다. 로드밸런서 뒤에서는 server.forward-headers-strategy로
 * X-Forwarded-For를 신뢰할 프록시에서만 읽게 설정해 둔다. (VerificationIpRateLimitInterceptor와 같은 방식)
 */
@Component
@RequiredArgsConstructor
public class GuestJoinRateLimitInterceptor implements HandlerInterceptor {

    public static final int MAX_GUEST_JOINS_PER_IP = 20;
    public static final Duration WINDOW = Duration.ofMinutes(1);

    private final GuestJoinRateLimitRepository rateLimitRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser) {
            return true;
        }
        if (rateLimitRepository.increase(request.getRemoteAddr(), WINDOW) > MAX_GUEST_JOINS_PER_IP) {
            throw new BusinessException(LiveStreamErrorCode.TOO_MANY_GUEST_JOINS);
        }
        return true;
    }
}
