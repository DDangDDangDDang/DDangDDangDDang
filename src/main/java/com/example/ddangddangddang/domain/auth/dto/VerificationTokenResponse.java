package com.example.ddangddangddang.domain.auth.dto;

/** 인증 완료 토큰. 다음 단계(회원가입, 비밀번호 재설정) 요청에 그대로 넣는다. 10분 동안 한 번만 쓸 수 있다. */
public record VerificationTokenResponse(String verificationToken) {
}