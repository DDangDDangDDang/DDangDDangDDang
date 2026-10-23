package com.example.ddangddangddang.domain.auth.dto;

import com.example.ddangddangddang.domain.auth.dto.LoginResponse.UserInfo;

// 카카오 로그인 결과. LOGIN / SIGNUP_REQUIRED
public record KakaoLoginResponse(
        Status status,
        String accessToken,
        Long expiresIn,
        UserInfo user,
        String signupToken,
        String email,
        String nickname
) {

    public enum Status {
        LOGIN, SIGNUP_REQUIRED
    }

    public static KakaoLoginResponse login(LoginResponse login) {
        return new KakaoLoginResponse(Status.LOGIN, login.accessToken(), login.expiresIn(), login.user(), null, null, null);
    }

    public static KakaoLoginResponse signupRequired(String signupToken, String email, String nickname) {
        return new KakaoLoginResponse(Status.SIGNUP_REQUIRED, null, null, null, signupToken, email, nickname);
    }
}
