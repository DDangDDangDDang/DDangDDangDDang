package com.example.ddangddangddang.domain.point.exception;

import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PointErrorCode implements ErrorCode {

    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "POINT_001", "포인트 지갑이 존재하지 않습니다."),
    WALLET_ALREADY_EXISTS(HttpStatus.CONFLICT, "POINT_002", "이미 포인트 지갑이 존재합니다."),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "POINT_003", "포인트 금액은 0보다 커야 합니다."),
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "POINT_004", "사용 가능한 포인트가 부족합니다."),
    INSUFFICIENT_HELD_BALANCE(HttpStatus.CONFLICT, "POINT_005", "홀딩된 포인트가 부족합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}