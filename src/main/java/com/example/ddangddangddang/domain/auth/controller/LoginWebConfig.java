package com.example.ddangddangddang.domain.auth.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 이메일·비밀번호 로그인 API에만 IP 제한을 건다. 소셜 로그인은 비밀번호를 받지 않으므로 대상이 아니다.
 */
@Configuration
@RequiredArgsConstructor
public class LoginWebConfig implements WebMvcConfigurer {

    private final LoginIpRateLimitInterceptor loginIpRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginIpRateLimitInterceptor)
                .addPathPatterns("/api/v1/auth/login");
    }
}
