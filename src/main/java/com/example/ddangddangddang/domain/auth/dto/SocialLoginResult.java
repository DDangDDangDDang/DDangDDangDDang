package com.example.ddangddangddang.domain.auth.dto;

// 서비스에서 컨트롤러로 넘기는 소셜 로그인 결과
public record SocialLoginResult(SocialLoginResponse response, String refreshToken) {

    public static SocialLoginResult login(LoginResult login) {
        return new SocialLoginResult(SocialLoginResponse.login(login.response()), login.refreshToken());
    }

    public static SocialLoginResult signupRequired(String signupToken, String email, String nickname) {
        return new SocialLoginResult(SocialLoginResponse.signupRequired(signupToken, email, nickname), null);
    }

    public boolean loggedIn() {
        return refreshToken != null;
    }
}