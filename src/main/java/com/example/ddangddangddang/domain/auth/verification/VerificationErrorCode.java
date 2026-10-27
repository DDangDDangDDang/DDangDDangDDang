package com.example.ddangddangddang.domain.auth.verification;

import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum VerificationErrorCode implements ErrorCode {

    INVALID_CODE(HttpStatus.BAD_REQUEST, "VERIFY_001", "인증번호가 올바르지 않거나 만료되었습니다."),
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "VERIFY_002", "인증 시도 횟수를 초과했습니다. 인증번호를 다시 요청해 주세요."),
    RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "VERIFY_003", "인증번호는 1분에 한 번만 요청할 수 있습니다."),
    INVALID_VERIFICATION_TOKEN(HttpStatus.BAD_REQUEST, "VERIFY_004", "인증이 만료되었습니다. 다시 인증해 주세요."),
    TOO_MANY_REQUESTS_FROM_IP(HttpStatus.TOO_MANY_REQUESTS, "VERIFY_005", "인증번호 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "VERIFY_006", "오늘 받을 수 있는 인증번호 횟수를 넘었습니다. 내일 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}