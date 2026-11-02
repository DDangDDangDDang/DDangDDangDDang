package com.example.ddangddangddang.domain.auth.verification;

/**
 * 인증번호의 용도. 용도마다 Redis 키를 나눠, 한 용도로 받은 인증을 다른 용도에 쓰지 못하게 한다.
 * (예: 회원가입용 휴대폰 인증 토큰으로 비밀번호를 재설정할 수 없음)
 */
public enum VerificationPurpose {
    SIGNUP_PHONE,
    FIND_EMAIL,
    RESET_PASSWORD,
    // 로그인한 회원이 이메일·휴대폰 번호를 바꿀 때 새 이메일·번호가 본인 것인지 확인한다. (UserContactService)
    CHANGE_EMAIL,
    CHANGE_PHONE
}
