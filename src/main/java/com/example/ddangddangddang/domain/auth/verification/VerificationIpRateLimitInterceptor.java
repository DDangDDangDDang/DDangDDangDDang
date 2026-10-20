package com.example.ddangddangddang.domain.auth.verification;

import com.example.ddangddangddang.global.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 인증번호 발송 API를 IP당 시간당 20회로 제한한다.
 *
 * 대상(번호·이메일)별 제한만 있으면, 번호를 바꿔가며 요청해 문자 비용을 계속 쓰게 만들 수 있다.
 * 가입·이메일 찾기·비밀번호 재설정을 합산해, 용도를 바꿔가며 요청해도 같이 센다.
 *
 * IP는 request.getRemoteAddr()로 얻는다. 로드밸런서 뒤에서는 이 값이 로드밸런서 IP가 되므로
 * server.forward-headers-strategy로 X-Forwarded-For를 신뢰할 프록시에서만 읽게 설정해 둔다.
 * (요청 헤더를 직접 읽으면 클라이언트가 X-Forwarded-For를 바꿔 보내 제한을 우회할 수 있다)
 */
@Component
@RequiredArgsConstructor
public class VerificationIpRateLimitInterceptor implements HandlerInterceptor {

    public static final int MAX_REQUESTS_PER_IP = 20;
    public static final Duration WINDOW = Duration.ofHours(1);

    private final VerificationRateLimitRepository rateLimitRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        long count = rateLimitRepository.increaseIpCount(request.getRemoteAddr(), WINDOW);
        if (count > MAX_REQUESTS_PER_IP) {
            throw new BusinessException(VerificationErrorCode.TOO_MANY_REQUESTS_FROM_IP);
        }
        return true;
    }
}