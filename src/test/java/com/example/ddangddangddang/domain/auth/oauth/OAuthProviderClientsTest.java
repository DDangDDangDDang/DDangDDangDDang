package com.example.ddangddangddang.domain.auth.oauth;

import com.example.ddangddangddang.domain.user.entity.SocialProvider;
import com.example.ddangddangddang.global.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class OAuthProviderClientsTest {

    @Test
    @DisplayName("경로의 제공자 이름을 대소문자 상관없이 해석하고, 모르는 이름은 OAUTH_006")
    void parse() {
        assertThat(OAuthProviderClients.parse("kakao")).isEqualTo(SocialProvider.KAKAO);
        assertThat(OAuthProviderClients.parse("GOOGLE")).isEqualTo(SocialProvider.GOOGLE);
        assertThatThrownBy(() -> OAuthProviderClients.parse("naver"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OAuthErrorCode.UNSUPPORTED_PROVIDER);
    }

    @Test
    @DisplayName("제공자별 클라이언트를 찾고, 등록되지 않은 제공자면 OAUTH_006")
    void get() {
        OAuthProviderClient kakao = mock(OAuthProviderClient.class);
        given(kakao.provider()).willReturn(SocialProvider.KAKAO);
        OAuthProviderClients clients = new OAuthProviderClients(List.of(kakao));

        assertThat(clients.get(SocialProvider.KAKAO)).isSameAs(kakao);
        assertThatThrownBy(() -> clients.get(SocialProvider.GOOGLE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OAuthErrorCode.UNSUPPORTED_PROVIDER);
    }
}
