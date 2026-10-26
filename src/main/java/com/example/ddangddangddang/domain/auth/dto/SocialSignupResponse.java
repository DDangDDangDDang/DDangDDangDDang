package com.example.ddangddangddang.domain.auth.dto;

import com.example.ddangddangddang.domain.auth.dto.LoginResponse.UserInfo;

/**
 * 소셜 가입 결과
 * 가입과 동시에 로그인
 *
 * @param linkedExistingAccount true면 새 회원이 아니라 휴대폰 번호가 같은 기존 계정에 소셜 계정을 연결
 */
public record SocialSignupResponse(
        boolean linkedExistingAccount,
        String accessToken,
        long expiresIn,
        UserInfo user
) {

    public static SocialSignupResponse of(boolean linkedExistingAccount, LoginResponse login) {
        return new SocialSignupResponse(linkedExistingAccount, login.accessToken(), login.expiresIn(), login.user());
    }
}