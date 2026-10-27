package com.example.ddangddangddang.domain.auth.dto;

// 서비스에서 컨트롤러로 넘기는 소셜 가입 결과
public record SocialSignupResult(
        SocialSignupResponse response,
        String refreshToken
) {

    public static SocialSignupResult of(boolean linkedExistingAccount, LoginResult login) {
        return new SocialSignupResult(SocialSignupResponse.of(linkedExistingAccount, login.response()), login.refreshToken());
    }
}