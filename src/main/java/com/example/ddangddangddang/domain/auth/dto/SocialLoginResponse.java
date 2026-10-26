package com.example.ddangddangddang.domain.auth.dto;

import com.example.ddangddangddang.domain.auth.dto.LoginResponse.UserInfo;

// 소셜 로그인 결과. LOGIN / SIGNUP_REQUIRED
public record SocialLoginResponse(
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

    public static SocialLoginResponse login(LoginResponse login) {
        return new SocialLoginResponse(Status.LOGIN, login.accessToken(), login.expiresIn(), login.user(), null, null, null);
    }

    public static SocialLoginResponse signupRequired(String signupToken, String email, String nickname) {
        return new SocialLoginResponse(Status.SIGNUP_REQUIRED, null, null, null, signupToken, email, nickname);
    }
}