package com.example.ddangddangddang.domain.auth.dto;

// 서비스에서 컨트롤러로 남기는 카카오 가입 결과
public record KakaoSignupResult(
        KakaoSignupResponse response,
        String refreshToken
) {

    public static KakaoSignupResult of(boolean linkedExistingAccount, LoginResult login) {
        return new KakaoSignupResult(KakaoSignupResponse.of(linkedExistingAccount, login.response()), login.refreshToken());
    }
}