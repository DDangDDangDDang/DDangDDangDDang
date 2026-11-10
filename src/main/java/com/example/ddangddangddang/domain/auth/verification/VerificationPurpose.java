package com.example.ddangddangddang.domain.auth.verification;

/**
 * 인증번호의 용도. 용도마다 Redis 키를 나눠, 한 용도로 받은 인증을 다른 용도에 쓰지 못하게 한다.
 * (예: 회원가입용 휴대폰 인증 토큰으로 비밀번호를 재설정할 수 없음)
 */
public enum VerificationPurpose {
    SIGNUP_PHONE,
    // 가입할 이메일이 본인 것인지 확인한다. (일반 가입, 제공자가 확인해 주지 않은 이메일로 하는 소셜 가입)
    SIGNUP_EMAIL,
    FIND_EMAIL,
    RESET_PASSWORD,
    // 로그인한 회원이 이메일·휴대폰 번호를 바꿀 때 새 이메일·번호가 본인 것인지 확인한다. (UserContactService)
    CHANGE_EMAIL,
    CHANGE_PHONE,
    // 로그인한 회원이 이메일·휴대폰 번호를 바꾸기 전에 본인임을 다시 확인한다. (비밀번호 또는 지금 휴대폰 번호, UserContactService)
    REAUTH
}
