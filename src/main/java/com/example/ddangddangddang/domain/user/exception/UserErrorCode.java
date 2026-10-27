package com.example.ddangddangddang.domain.user.exception;

import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "USER_001", "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "USER_002", "이미 사용 중인 닉네임입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_003", "존재하지 않는 회원입니다."),
    DUPLICATE_PHONE_NUMBER(HttpStatus.CONFLICT, "USER_004", "이미 가입된 휴대폰 번호입니다."),
    NICKNAME_CHANGE_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "USER_005", "닉네임을 바꾼 뒤 일정 기간이 지나야 다시 바꿀 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}