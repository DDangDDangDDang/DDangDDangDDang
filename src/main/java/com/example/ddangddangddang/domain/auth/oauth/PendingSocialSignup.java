package com.example.ddangddangddang.domain.auth.oauth;

import com.example.ddangddangddang.domain.user.entity.SocialProvider;

// 소셜 로그인은 했지만 추가 정보 입력 전(가입대기)
public record PendingSocialSignup(
        SocialProvider provider,
        String providerUserId,
        String email,
        String nickname
) {
}
