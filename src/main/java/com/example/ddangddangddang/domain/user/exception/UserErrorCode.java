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
    NICKNAME_CHANGE_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "USER_005", "닉네임을 바꾼 뒤 일정 기간이 지나야 다시 바꿀 수 있습니다."),
    CONTENT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "USER_006", "소개에 사용할 수 없는 내용이 포함되어 있습니다."),
    UNSUPPORTED_PROFILE_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "USER_007", "프로필 이미지는 JPG, PNG, WEBP, GIF만 올릴 수 있습니다."),
    PROFILE_IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "USER_008", "프로필 이미지는 5MB 이하만 올릴 수 있습니다."),
    PROFILE_IMAGE_NOT_UPLOADED(HttpStatus.BAD_REQUEST, "USER_009", "올린 이미지를 찾을 수 없습니다. 다시 올려 주세요."),
    SAME_EMAIL(HttpStatus.BAD_REQUEST, "USER_010", "지금 쓰고 있는 이메일입니다."),
    SAME_PHONE_NUMBER(HttpStatus.BAD_REQUEST, "USER_011", "지금 쓰고 있는 휴대폰 번호입니다."),
    NICKNAME_TICKET_NOT_NEEDED(HttpStatus.BAD_REQUEST, "USER_012", "지금은 변경권 없이 닉네임을 바꿀 수 있습니다."),
    SAME_NICKNAME(HttpStatus.BAD_REQUEST, "USER_013", "지금 쓰고 있는 닉네임입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
