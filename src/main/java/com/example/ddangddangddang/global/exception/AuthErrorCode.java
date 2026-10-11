package com.example.ddangddangddang.global.security;

import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 인증·인가 에러 코드.
 * 보안 필터(global)에서도 사용하므로 auth 도메인이 아닌 global.security에 둔다.
 */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH_001", "인증 토큰이 필요합니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_002", "액세스 토큰이 만료되었습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_003", "유효하지 않은 토큰입니다."),
    LOGGED_OUT_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_004", "로그아웃된 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_005", "다시 로그인해 주세요."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_006", "접근 권한이 없습니다."),
    NOT_RESOURCE_OWNER(HttpStatus.FORBIDDEN, "AUTH_007", "본인의 리소스만 접근할 수 있습니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_008", "이메일 또는 비밀번호가 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}