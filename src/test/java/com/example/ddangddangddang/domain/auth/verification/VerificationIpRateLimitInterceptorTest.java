package com.example.ddangddangddang.domain.auth.verification;

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
class VerificationIpRateLimitInterceptorTest {

    @InjectMocks
    private VerificationIpRateLimitInterceptor interceptor;

    @Mock
    private VerificationRateLimitRepository rateLimitRepository;

    @Test
    @DisplayName("요청한 IP 기준으로 세고, 20번째 요청까지는 통과시킨다")
    void allowUpToLimit() {
        given(rateLimitRepository.increaseIpCount("203.0.113.7", VerificationIpRateLimitInterceptor.WINDOW))
                .willReturn((long) VerificationIpRateLimitInterceptor.MAX_REQUESTS_PER_IP);

        boolean proceed = interceptor.preHandle(request("203.0.113.7"), new MockHttpServletResponse(), new Object());

        assertThat(proceed).isTrue();
    }

    @Test
    @DisplayName("21번째 요청부터는 VERIFY_005로 막는다")
    void blockOverLimit() {
        given(rateLimitRepository.increaseIpCount("203.0.113.7", VerificationIpRateLimitInterceptor.WINDOW))
                .willReturn((long) VerificationIpRateLimitInterceptor.MAX_REQUESTS_PER_IP + 1);

        assertThatThrownBy(() -> interceptor.preHandle(request("203.0.113.7"), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(VerificationErrorCode.TOO_MANY_REQUESTS_FROM_IP);
    }

    @Test
    @DisplayName("요청 헤더의 X-Forwarded-For는 직접 읽지 않는다 (클라이언트가 바꿔 보내 우회하지 못하게)")
    void ignoreForwardedHeader() {
        MockHttpServletRequest request = request("203.0.113.7");
        request.addHeader("X-Forwarded-For", "1.1.1.1");
        given(rateLimitRepository.increaseIpCount("203.0.113.7", VerificationIpRateLimitInterceptor.WINDOW)).willReturn(1L);

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }

    private MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/signup/phone/code");
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}