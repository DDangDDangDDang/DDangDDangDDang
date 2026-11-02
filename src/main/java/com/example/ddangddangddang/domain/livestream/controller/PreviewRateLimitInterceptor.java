package com.example.ddangddangddang.domain.livestream.controller;

import com.example.ddangddangddang.domain.livestream.exception.LiveStreamErrorCode;
import com.example.ddangddangddang.domain.livestream.repository.GuestJoinRateLimitRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 홈 미리보기(POST /live-streams/{id}/preview)를 IP당 1분에 60회로 제한한다.
 * 미리보기는 회원도 접속마다 새 identity를 쓰므로 회원·손님 모두 센다. 화면은 카드에 마우스를 잠시 올려 둘 때만 부르므로 보통은 닿지 않는다.
 * IP는 GuestJoinRateLimitInterceptor와 같은 방식으로 얻는다.
 */
@Component
@RequiredArgsConstructor
public class PreviewRateLimitInterceptor implements HandlerInterceptor {

    public static final int MAX_PREVIEWS_PER_IP = 60;
    public static final Duration WINDOW = Duration.ofMinutes(1);

    private final GuestJoinRateLimitRepository rateLimitRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (rateLimitRepository.increasePreview(request.getRemoteAddr(), WINDOW) > MAX_PREVIEWS_PER_IP) {
            throw new BusinessException(LiveStreamErrorCode.TOO_MANY_PREVIEWS);
        }
        return true;
    }
}
