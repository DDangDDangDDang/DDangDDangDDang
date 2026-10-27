package com.example.ddangddangddang.domain.auth.oauth;

import com.example.ddangddangddang.global.exception.BusinessException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 실제 카카오를 부르지 않고, 가짜 서버(MockRestServiceServer)로 요청 형식과 응답 처리를 확인한다.
 */
class KakaoOAuthClientTest {

    private static final String AUTH_URL = "https://kauth.kakao.com";
    private static final String API_URL = "https://kapi.kakao.com";
    private static final KakaoOAuthProperties PROPERTIES = new KakaoOAuthProperties(
            "client-id", "client-secret", "http://localhost:5173/oauth/kakao/callback", null, null, null, null);

    private MockRestServiceServer server;
    private KakaoOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoOAuthClient(builder.build(), PROPERTIES);
    }

    @Test
    @DisplayName("인가 코드를 서버 설정값(client_id, redirect_uri, client_secret)과 함께 보내 토큰을 받고, 그 토큰으로 회원 정보를 조회한다")
    void success() {
        expectToken();
        server.expect(requestTo(API_URL + KakaoOAuthClient.USER_PATH))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer kakao-access-token"))
                .andRespond(withSuccess("""
                        {"id": 1234567890,
                         "kakao_account": {"email": "user@kakao.com", "is_email_valid": true, "is_email_verified": true,
                                           "profile": {"nickname": "카카오닉"}}}
                        """, MediaType.APPLICATION_JSON));

        SocialUser user = client.getUser("auth-code");

        assertThat(user).isEqualTo(new SocialUser("1234567890", "user@kakao.com", "카카오닉"));
        server.verify();
    }

    @Test
    @DisplayName("카카오가 검증하지 않은 이메일은 쓰지 않는다")
    void unverifiedEmail() {
        expectToken();
        expectUser("""
                {"id": 1, "kakao_account": {"email": "user@kakao.com", "is_email_valid": true, "is_email_verified": false}}
                """);

        assertThat(client.getUser("auth-code").email()).isNull();
    }

    @Test
    @DisplayName("이메일·닉네임 동의를 하지 않았으면 둘 다 null이다")
    void noConsent() {
        expectToken();
        expectUser("""
                {"id": 1, "kakao_account": {}}
                """);

        SocialUser user = client.getUser("auth-code");

        assertThat(user.id()).isEqualTo("1");
        assertThat(user.email()).isNull();
        assertThat(user.nickname()).isNull();
    }

    @Test
    @DisplayName("카카오가 인가 코드를 거절하면(4xx) OAUTH_001")
    void invalidCode() {
        server.expect(requestTo(AUTH_URL + KakaoOAuthClient.TOKEN_PATH))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": \"invalid_grant\", \"error_code\": \"KOE320\"}"));

        assertThatThrownBy(() -> client.getUser("used-code"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OAuthErrorCode.INVALID_AUTHORIZATION_CODE);
    }

    @Test
    @DisplayName("카카오 서버 오류는 OAUTH_002")
    void serverError() {
        server.expect(requestTo(AUTH_URL + KakaoOAuthClient.TOKEN_PATH)).andRespond(withServerError());

        assertThatThrownBy(() -> client.getUser("auth-code"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OAuthErrorCode.PROVIDER_ERROR);
    }

    @Test
    @DisplayName("타임아웃 등 통신 오류는 OAUTH_002")
    void timeout() {
        expectToken();
        server.expect(requestTo(API_URL + KakaoOAuthClient.USER_PATH))
                .andRespond(withException(new java.net.SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.getUser("auth-code"))
                .isInstanceOf(BusinessException.class)
                .hasCauseInstanceOf(ResourceAccessException.class)
                .extracting("errorCode").isEqualTo(OAuthErrorCode.PROVIDER_ERROR);
    }

    @Test
    @DisplayName("REST API 키가 설정되지 않았으면 카카오를 부르지 않고 OAUTH_005")
    void notConfigured() {
        KakaoOAuthClient notConfigured = new KakaoOAuthClient(RestClient.builder().build(),
                new KakaoOAuthProperties("", "", "", null, null, null, null));

        assertThatThrownBy(() -> notConfigured.getUser("auth-code"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OAuthErrorCode.NOT_CONFIGURED);
    }

    @Test
    @DisplayName("타임아웃을 설정하지 않으면 기본값을 쓴다")
    void defaultTimeouts() {
        assertThat(PROPERTIES.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(PROPERTIES.readTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    private void expectToken() {
        server.expect(requestTo(AUTH_URL + KakaoOAuthClient.TOKEN_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(java.util.Map.of(
                        "grant_type", "authorization_code",
                        "client_id", "client-id",
                        "redirect_uri", "http://localhost:5173/oauth/kakao/callback",
                        "code", "auth-code",
                        "client_secret", "client-secret")))
                .andRespond(withSuccess("{\"access_token\": \"kakao-access-token\", \"token_type\": \"bearer\"}",
                        MediaType.APPLICATION_JSON));
    }

    private void expectUser(String body) {
        server.expect(requestTo(API_URL + KakaoOAuthClient.USER_PATH))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}
