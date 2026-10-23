package com.example.ddangddangddang.domain.auth.dto;

import com.example.ddangddangddang.domain.auth.dto.LoginResponse.UserInfo;

/**
 * 카카오 가입 결과
 * 가입과 동시에 로그인
 *
 * @param linkedExistingAccount ture면 새 회원이 아니라 휴대폰 번호가 같은 기존 계정에 카카오를 연결
 */
public record KakaoSignupResponse(
        boolean linkedExistingAccount,
        String accessToken,
        long expiresIn,
        UserInfo user
) {

    public static KakaoSignupResponse of(boolean linkedExistingAccount, LoginResponse login) {
        return new KakaoSignupResponse(linkedExistingAccount, login.accessToken(), login.expiresIn(), login.user());
    }
}