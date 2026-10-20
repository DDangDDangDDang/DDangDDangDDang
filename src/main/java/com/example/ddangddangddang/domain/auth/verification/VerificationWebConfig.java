package com.example.ddangddangddang.domain.auth.verification;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 인증번호를 "보내는" API에만 IP 제한을 건다. 확인(verify) API는 인증번호별 5회 제한이 이미 있다.
 */
@Configuration
@RequiredArgsConstructor
public class VerificationWebConfig implements WebMvcConfigurer {

    private final VerificationIpRateLimitInterceptor ipRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ipRateLimitInterceptor)
                .addPathPatterns(
                        "/api/v1/auth/signup/phone/code",
                        "/api/v1/auth/find-email/code",
                        "/api/v1/auth/password-reset/code"
                );
    }
}