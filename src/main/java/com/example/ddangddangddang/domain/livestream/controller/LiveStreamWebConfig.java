package com.example.ddangddangddang.domain.livestream.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 토큰 없이 부를 수 있는 쓰기 성격의 방송 API(손님 시청 입장, 홈 미리보기)에 IP 제한을 건다.
 */
@Configuration
@RequiredArgsConstructor
public class LiveStreamWebConfig implements WebMvcConfigurer {

    private final GuestJoinRateLimitInterceptor guestJoinRateLimitInterceptor;
    private final PreviewRateLimitInterceptor previewRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(guestJoinRateLimitInterceptor).addPathPatterns("/api/v1/live-streams/*/join");
        registry.addInterceptor(previewRateLimitInterceptor).addPathPatterns("/api/v1/live-streams/*/preview");
    }
}
