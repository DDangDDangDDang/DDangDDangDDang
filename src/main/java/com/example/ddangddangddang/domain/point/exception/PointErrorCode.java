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

    INVALID_POINT_AMOUNT(HttpStatus.BAD_REQUEST, "POINT_011", "포인트 금액이 0보다 커야합니다."),
    INSUFFICIENT_POINT(HttpStatus.BAD_REQUEST, "POINT_012", "사용 가능한 포인트가 부족합니다."),
    INVALID_CHARGE_STATUS(HttpStatus.BAD_REQUEST, "POINT_013", "현재 상태에서는 포인트 충전이 불가능합니다"),
    INVALID_PAYMENT_ID(HttpStatus.BAD_REQUEST, "POINT_014", "결제 ID가 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}