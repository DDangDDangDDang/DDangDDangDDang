package com.example.ddangddangddang.domain.auth.oauth;

import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OAuthErrorCode implements ErrorCode {

    INVALID_AUTHORIZATION_CODE(HttpStatus.BAD_REQUEST, "OAUTH_001", "카카오 로그인에 실패했습니다. 다시 시도해 주세요."),
    PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "OAUTH_002", "카카오와 통신 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),
    SIGNUP_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "OAUTH_003", "가입 시간이 지났습니다. 카카오 로그인부터 다시 진행해 주세요."),
    ALREADY_LINKED_OTHER_ACCOUNT(HttpStatus.CONFLICT, "OAUTH_004", "이 휴대폰 번호의 계정에는 이미 다른 카카오 계정이 연결되어 있습니다."),
    NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "OAUTH_005", "카카오 로그인이 설정되지 않았습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}