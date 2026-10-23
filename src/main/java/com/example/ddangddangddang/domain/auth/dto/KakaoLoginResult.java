package com.example.ddangddangddang.domain.auth.dto;

// 서비스에서 컨트롤러로 넘기는 카카오 로그인 결과
public record KakaoLoginResult(KakaoLoginResponse response, String refreshToken) {

    public static KakaoLoginResult login(LoginResult login) {
        return new KakaoLoginResult(KakaoLoginResponse.login(login.response()), login.refreshToken());
    }

    public static KakaoLoginResult signupRequired(String signupToken, String email, String nickname) {
        return new KakaoLoginResult(KakaoLoginResponse.signupRequired(signupToken, email, nickname), null);
    }

    public boolean loggedIn() {
        return refreshToken != null;
    }
}