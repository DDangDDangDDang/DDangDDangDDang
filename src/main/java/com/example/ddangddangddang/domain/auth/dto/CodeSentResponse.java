package com.example.ddangddangddang.domain.auth.dto;

/** 인증번호 발송 응답. 프론트는 expiresIn(초)으로 남은 시간을 보여준다. */
public record CodeSentResponse(long expiresIn) {
}