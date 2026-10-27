package com.example.ddangddangddang.domain.auth.oauth;

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
import com.example.ddangddangddang.domain.user.entity.SocialProvider;

/**
 * 카카오 로그인 REST API 클라이언트.
 * (https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api)
 * <p>
 * 1. 인가 코드 → 카카오 토큰 (kauth /oauth/token)
 * 2. 카카오 토큰 → 회원 정보 (kapi /v2/user/me)
 * 카카오 토큰은 회원 정보를 받는 데만 쓰고 저장하지 않는다. 인가 코드와 토큰은 로그에 남기지 않는다.
 */
@Slf4j
@Component
public class KakaoOAuthClient implements OAuthProviderClient {

    static final String TOKEN_PATH = "/oauth/token";
    static final String USER_PATH = "/v2/user/me";

    private final RestClient restClient;
    private final KakaoOAuthProperties properties;

    @Autowired
    public KakaoOAuthClient(KakaoOAuthProperties properties) {
        this(createRestClient(properties), properties);
    }

    KakaoOAuthClient(RestClient restClient, KakaoOAuthProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    private static RestClient createRestClient(KakaoOAuthProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder().requestFactory(requestFactory).build();
    }
    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public SocialUser getUser(String authorizationCode) {
        if (!properties.isConfigured()) {
            throw new BusinessException(OAuthErrorCode.NOT_CONFIGURED);
        }
        return fetchUser(fetchAccessToken(authorizationCode));
    }

    /**
     * 인가 코드는 한 번만 쓸 수 있고 금방 만료된다. 카카오가 4xx로 거절하면 사용자가 다시 로그인해야 하는 경우다.
     * client_id, redirect_uri는 클라이언트가 보낸 값이 아니라 서버 설정값만 쓴다.
     */
    private String fetchAccessToken(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", authorizationCode);
        if (StringUtils.hasText(properties.clientSecret())) {
            form.add("client_secret", properties.clientSecret());
        }

        try {
            JsonNode response = restClient.post()
                    .uri(properties.authBaseUrl() + TOKEN_PATH)
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
                // KOE320(코드 재사용·만료), KOE006(redirect_uri 불일치) 등. 응답 본문에는 코드가 없어 그대로 남긴다
                log.warn("카카오 토큰 발급 거절: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
                throw new BusinessException(OAuthErrorCode.INVALID_AUTHORIZATION_CODE, e);
            }
            log.error("카카오 토큰 발급 실패: status={}", e.getStatusCode(), e);
            throw new BusinessException(OAuthErrorCode.PROVIDER_ERROR, e);
        } catch (RestClientException e) {
            // 타임아웃도 여기로 온다 (ResourceAccessException)
            log.error("카카오 토큰 발급 통신 오류", e);
            throw new BusinessException(OAuthErrorCode.PROVIDER_ERROR, e);
        }
    }

    private SocialUser fetchUser(String accessToken) {
        try {
            JsonNode response = restClient.get()
                    .uri(properties.apiBaseUrl() + USER_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.hasNonNull("id")) {
                throw new BusinessException(OAuthErrorCode.PROVIDER_ERROR);
            }
            return toKakaoUser(response);
        } catch (RestClientException e) {
            log.error("카카오 회원 정보 조회 실패", e);
            throw new BusinessException(OAuthErrorCode.PROVIDER_ERROR, e);
        }
    }

    /**
     * 이메일은 유효하고(is_email_valid) 카카오가 인증한(is_email_verified) 경우에만 쓴다.
     */
    private SocialUser toKakaoUser(JsonNode response) {
        JsonNode account = response.path("kakao_account");
        String email = null;
        if (account.path("is_email_valid").asBoolean(false) && account.path("is_email_verified").asBoolean(false)) {
            email = account.path("email").asString(null);
        }
        String nickname = account.path("profile").path("nickname").asString(null);
        if (nickname == null) {
            nickname = response.path("properties").path("nickname").asString(null);
        }
        return new SocialUser(response.path("id").asString(), email, nickname);
    }
}