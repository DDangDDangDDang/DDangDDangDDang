package com.example.ddangddangddang.domain.auth.controller;

import com.example.ddangddangddang.domain.auth.exception.AuthErrorCode;
import com.example.ddangddangddang.domain.auth.repository.LoginAttemptRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class LoginIpRateLimitInterceptorTest {

    @InjectMocks
    private LoginIpRateLimitInterceptor interceptor;

    @Mock
    private LoginAttemptRepository loginAttemptRepository;

    @Test
    @DisplayName("요청한 IP 기준으로 세고, 30번째 요청까지는 통과시킨다")
    void allowUpToLimit() {
        given(loginAttemptRepository.increaseIpCount("203.0.113.7", LoginIpRateLimitInterceptor.WINDOW))
                .willReturn((long) LoginIpRateLimitInterceptor.MAX_REQUESTS_PER_IP);

        boolean proceed = interceptor.preHandle(request("203.0.113.7"), new MockHttpServletResponse(), new Object());

        assertThat(proceed).isTrue();
    }

    @Test
    @DisplayName("31번째 요청부터는 AUTH_011로 막는다")
    void blockOverLimit() {
        given(loginAttemptRepository.increaseIpCount("203.0.113.7", LoginIpRateLimitInterceptor.WINDOW))
                .willReturn((long) LoginIpRateLimitInterceptor.MAX_REQUESTS_PER_IP + 1);

        assertThatThrownBy(() -> interceptor.preHandle(request("203.0.113.7"), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.TOO_MANY_LOGIN_REQUESTS);
    }

    private MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
