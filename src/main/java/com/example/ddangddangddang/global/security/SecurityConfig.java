package com.example.ddangddangddang.global.security;

import com.example.ddangddangddang.global.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * 토큰 없이 호출할 수 있는 POST API. API 명세(OpenApiConfig)도 이 목록으로 "인증 불필요"를 표시한다.
     */
    public static final String[] PUBLIC_POST_APIS = {
            "/api/v1/auth/signup",
            "/api/v1/auth/signup/phone/code",
            "/api/v1/auth/signup/phone/verify",
            "/api/v1/auth/find-email/code",
            "/api/v1/auth/find-email/verify",
            "/api/v1/auth/password-reset/code",
            "/api/v1/auth/password-reset/verify",
            "/api/v1/auth/password-reset",
            "/api/v1/auth/login",
            "/api/v1/auth/reissue",
            "/api/v1/auth/oauth/{provider}",
            "/api/v1/auth/oauth/{provider}/signup",
            "/api/v1/auth/oauth/{provider}/phone/code",
            // 포트원 결제 웹훅. 토큰 대신 웹훅 서명으로 검증한다.
            "/api/v1/points/charges/webhook",
            // 방송 시청 입장. 로그인하지 않으면 손님으로 입장한다. (LiveStreamService.joinAsGuest)
            "/api/v1/live-streams/{liveStreamId}/join"
    };

    /**
     * 로그인하지 않은 사람도 볼 수 있는 조회 API (방송·경매 둘러보기). API 명세도 이 목록으로 "인증 불필요"를 표시한다.
     * 경로 변수({...})는 한 구간만 맞으므로 /live-streams/me/active 같은 내 정보 API는 여기에 걸리지 않는다.
     */
    public static final String[] PUBLIC_GET_APIS = {
            "/api/v1/live-streams",
            "/api/v1/live-streams/home",
            "/api/v1/live-streams/{liveStreamId}",
            "/api/v1/auctions",
            "/api/v1/auctions/popular",
            "/api/v1/auctions/{auctionId}",
            "/api/v1/auctions/{auctionId}/bids",
            "/api/v1/lives/{liveId}/auctions",
            "/api/v1/categories",
            "/api/v1/products/{productId}",
            "/api/v1/sellers/{sellerId}/profile",
            "/api/v1/sellers/{sellerId}/auctions"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_APIS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_APIS).permitAll()
                        // 브라우저는 WebSocket 핸드셰이크에 Authorization 헤더를 붙일 수 없다.
                        // 핸드셰이크는 열어 두고 인증은 STOMP CONNECT 프레임에서 한다. (StompAuthChannelInterceptor)
                        .requestMatchers(HttpMethod.GET, "/ws").permitAll()
                        // 로드밸런서(ALB)가 토큰 없이 서버 상태를 확인한다. 노출 범위는 application.yml의 management 설정 참고
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        // API 명세와 Swagger UI. 운영(prod)에서는 springdoc 설정으로 꺼져 있어 404가 된다.
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    /**
     * JwtAuthenticationFilter는 @Component라서 서블릿 필터로도 자동 등록된다.
     * 그러면 Security 필터 체인 밖에서 한 번 더 실행되므로 자동 등록을 끈다.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}