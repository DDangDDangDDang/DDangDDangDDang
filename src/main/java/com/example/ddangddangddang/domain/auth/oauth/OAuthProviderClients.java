package com.example.ddangddangddang.domain.auth.oauth;

import com.example.ddangddangddang.domain.user.entity.SocialProvider;
import com.example.ddangddangddang.global.exception.BusinessException;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 제공자별 클라이언트 모음. API 경로의 제공자 이름(kakao, google)도 여기서 해석한다. */
@Component
public class OAuthProviderClients {

    private final Map<SocialProvider, OAuthProviderClient> clients = new EnumMap<>(SocialProvider.class);

    public OAuthProviderClients(List<OAuthProviderClient> clients) {
        clients.forEach(client -> this.clients.put(client.provider(), client));
    }

    public OAuthProviderClient get(SocialProvider provider) {
        OAuthProviderClient client = clients.get(provider);
        if (client == null) {
            throw new BusinessException(OAuthErrorCode.UNSUPPORTED_PROVIDER);
        }
        return client;
    }

    /** 경로의 제공자 이름(kakao, google) → SocialProvider. 지원하지 않는 이름이면 OAUTH_006 */
    public static SocialProvider parse(String name) {
        try {
            return SocialProvider.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(OAuthErrorCode.UNSUPPORTED_PROVIDER);
        }
    }
}