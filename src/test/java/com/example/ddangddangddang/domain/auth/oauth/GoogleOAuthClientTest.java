package com.example.ddangddangddang.domain.auth.oauth;

import com.example.ddangddangddang.domain.user.entity.SocialProvider;
import com.example.ddangddangddang.global.exception.BusinessException;
import java.util.Map;
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
 * 실제 구글을 부르지 않고, 가짜 서버(MockRestServiceServer)로 요청 형식과 응답 처리를 확인한다.
 */
class GoogleOAuthClientTest {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";
    private static final GoogleOAuthProperties PROPERTIES = new GoogleOAuthProperties(
            "client-id", "client-secret", "http://localhost:5173/oauth/google/callback", null, null, null, null);

    private MockRestServiceServer server;
    private GoogleOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GoogleOAuthClient(builder.build(), PROPERTIES);
    }

    @Test
    @DisplayName("제공자는 GOOGLE이다")
    void provider() {
        assertThat(client.provider()).isEqualTo(SocialProvider.GOOGLE);
    }

    @Test
    @DisplayName("인가 코드를 서버 설정값(client_id, client_secret, redirect_uri)과 함께 보내 토큰을 받고, 그 토큰으로 회원 정보를 조회한다")
    void success() {
        expectToken();
        server.expect(requestTo(USERINFO_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer google-access-token"))
                .andRespond(withSuccess("""
                        {"sub": "109876543210", "email": "user@gmail.com", "email_verified": true, "name": "홍길동"}
                        """, MediaType.APPLICATION_JSON));

        SocialUser user = client.getUser("auth-code");

        assertThat(user).isEqualTo(new SocialUser("109876543210", "user@gmail.com", "홍길동"));
        server.verify();
    }

    @Test
    @DisplayName("구글이 확인하지 않은 이메일은 쓰지 않는다")
    void unverifiedEmail() {
        expectToken();
        expectUser("""
                {"sub": "1", "email": "user@example.com", "email_verified": false, "name": "홍길동"}
                """);

        assertThat(client.getUser("auth-code").email()).isNull();
    }

    @Test
    @DisplayName("구글이 인가 코드를 거절하면(4xx) OAUTH_001")
    void invalidCode() {
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": \"invalid_grant\", \"error_description\": \"Bad Request\"}"));

        assertThatThrownBy(() -> client.getUser("used-code"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OAuthErrorCode.INVALID_AUTHORIZATION_CODE);
    }

    @Test
    @DisplayName("구글 서버 오류는 OAUTH_002")
    void serverError() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.getUser("auth-code"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OAuthErrorCode.PROVIDER_ERROR);
    }

    @Test
    @DisplayName("타임아웃 등 통신 오류는 OAUTH_002")
    void timeout() {
        expectToken();
        server.expect(requestTo(USERINFO_URL))
                .andRespond(withException(new java.net.SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.getUser("auth-code"))
                .isInstanceOf(BusinessException.class)
                .hasCauseInstanceOf(ResourceAccessException.class)
                .extracting("errorCode").isEqualTo(OAuthErrorCode.PROVIDER_ERROR);
    }

    @Test
    @DisplayName("클라이언트 ID나 보안 비밀번호가 없으면 구글을 부르지 않고 OAUTH_005")
    void notConfigured() {
        GoogleOAuthClient notConfigured = new GoogleOAuthClient(RestClient.builder().build(),
                new GoogleOAuthProperties("client-id", "", "http://localhost:5173/oauth/google/callback", null, null, null, null));

        assertThatThrownBy(() -> notConfigured.getUser("auth-code"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OAuthErrorCode.NOT_CONFIGURED);
    }

    private void expectToken() {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of(
                        "grant_type", "authorization_code",
                        "client_id", "client-id",
                        "client_secret", "client-secret",
                        "redirect_uri", "http://localhost:5173/oauth/google/callback",
                        "code", "auth-code")))
                .andRespond(withSuccess("{\"access_token\": \"google-access-token\", \"token_type\": \"Bearer\"}",
                        MediaType.APPLICATION_JSON));
    }

    private void expectUser(String body) {
        server.expect(requestTo(USERINFO_URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}
