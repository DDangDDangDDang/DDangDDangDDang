package com.example.ddangddangddang.domain.auth.oauth;

import com.example.ddangddangddang.domain.user.entity.SocialProvider;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * 구글 로그인(OpenID Connect) 클라이언트. (https://developers.google.com/identity/openid-connect/openid-connect)
 *
 * 1. 인가 코드 → 구글 토큰 (POST oauth2.googleapis.com/token)
 * 2. 구글 토큰 → 회원 정보 (GET openidconnect.googleapis.com/v1/userinfo): sub, email, email_verified, name
 * 구글 토큰은 회원 정보를 받는 데만 쓰고 저장하지 않는다. 인가 코드와 토큰은 로그에 남기지 않는다.
 */
@Slf4j
@Component
public class GoogleOAuthClient implements OAuthProviderClient {

    private final RestClient restClient;
    private final GoogleOAuthProperties properties;

    @Autowired
    public GoogleOAuthClient(GoogleOAuthProperties properties) {
        this(createRestClient(properties), properties);
    }

    GoogleOAuthClient(RestClient restClient, GoogleOAuthProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    private static RestClient createRestClient(GoogleOAuthProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.GOOGLE;
    }

    @Override
    public SocialUser getUser(String authorizationCode) {
        if (!properties.isConfigured()) {
            throw new BusinessException(OAuthErrorCode.NOT_CONFIGURED);
        }
        return fetchUser(fetchAccessToken(authorizationCode));
    }

    /** client_id, client_secret, redirect_uri는 클라이언트가 보낸 값이 아니라 서버 설정값만 쓴다. */
    private String fetchAccessToken(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", authorizationCode);

        try {
            JsonNode response = restClient.post()
                    .uri(properties.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            String accessToken = response == null ? null : response.path("access_token").asString(null);
            if (!StringUtils.hasText(accessToken)) {
                throw new BusinessException(OAuthErrorCode.PROVIDER_ERROR);
            }
            return accessToken;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                // invalid_grant(코드 재사용·만료), redirect_uri_mismatch 등. 응답 본문에는 코드가 없어 그대로 남긴다
                log.warn("구글 토큰 발급 거절: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
                throw new BusinessException(OAuthErrorCode.INVALID_AUTHORIZATION_CODE, e);
            }
            log.error("구글 토큰 발급 실패: status={}", e.getStatusCode(), e);
            throw new BusinessException(OAuthErrorCode.PROVIDER_ERROR, e);
        } catch (RestClientException e) {
            // 타임아웃도 여기로 온다 (ResourceAccessException)
            log.error("구글 토큰 발급 통신 오류", e);
            throw new BusinessException(OAuthErrorCode.PROVIDER_ERROR, e);
        }
    }

    private SocialUser fetchUser(String accessToken) {
        try {
            JsonNode response = restClient.get()
                    .uri(properties.userInfoUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.hasNonNull("sub")) {
                throw new BusinessException(OAuthErrorCode.PROVIDER_ERROR);
            }
            return toSocialUser(response);
        } catch (RestClientException e) {
            log.error("구글 회원 정보 조회 실패", e);
            throw new BusinessException(OAuthErrorCode.PROVIDER_ERROR, e);
        }
    }

    /** sub가 구글 회원 식별자다. 이메일은 구글이 확인한(email_verified) 경우에만 쓴다. */
    private SocialUser toSocialUser(JsonNode response) {
        String email = response.path("email_verified").asBoolean(false) ? response.path("email").asString(null) : null;
        return new SocialUser(response.path("sub").asString(), email, response.path("name").asString(null));
    }
}