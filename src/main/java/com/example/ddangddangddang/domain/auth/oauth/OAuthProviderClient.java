package com.example.ddangddangddang.domain.auth.oauth;

import com.example.ddangddangddang.domain.user.entity.SocialProvider;

// 소셜 로그인 제공자 클라이언트
public interface OAuthProviderClient {

    SocialProvider provider();

    SocialUser getUser(String authorizationCode) ;
}
