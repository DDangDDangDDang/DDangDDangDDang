package com.example.ddangddangddang.global.loadtest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 부하 테스트 중 Prometheus가 토큰 없이 서버 지표를 가져가도록 지표 경로만 연다. loadtest 프로필에서만 등록된다.
 *
 * 기본 보안 설정(SecurityConfig)은 모든 요청을 다루는 체인이라, 이 체인을 먼저 검사하도록 순서를 가장 앞에 둔다.
 * 어떤 엔드포인트를 노출할지는 application-loadtest.yml의 management.endpoints.web.exposure가 정한다.
 * 운영에서는 이 클래스가 없고 health만 노출하므로 지표가 열리지 않는다.
 */
@Configuration
@Profile("loadtest & !prod")
public class LoadTestMetricsSecurityConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain loadTestMetricsFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET).permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }
}
