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
    INVALID_PAYMENT_ID(HttpStatus.BAD_REQUEST, "POINT_014", "결제 ID가 올바르지 않습니다."),

    CHARGE_NOT_FOUND(HttpStatus.NOT_FOUND, "POINT_021", "충전 요청이 존재하지 않습니다."),
    CHARGE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "POINT_022", "본인의 충전 요청만 처리할 수 있습니다."),

    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "POINT_031", "결제 내역을 찾을 수 없습니다."),
    PAYMENT_GATEWAY_ERROR(HttpStatus.BAD_GATEWAY, "POINT_032", "결제사와 통신 중 오류가 발생했습니다."),

    EXCHANGE_NOT_FOUND(HttpStatus.NOT_FOUND, "POINT_041", "환전 신청이 존재하지 않습니다."),
    EXCHANGE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "POINT_042", "본인의 환전 신청만 처리할 수 있습니다."),
    INVALID_EXCHANGE_STATUS(HttpStatus.BAD_REQUEST, "POINT_043", "현재 상태에서는 처리할 수 없는 환전 신청입니다."),
    EXCHANGE_AMOUNT_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "POINT_044", "환전 금액은 1,000 이상 1,000,000 이하여야 합니다."),
    INVALID_BANK_ACCOUNT(HttpStatus.BAD_REQUEST, "POINT_045", "계좌 정보가 올바르지 않습니다."),
    INVALID_PAYOUT_ID(HttpStatus.BAD_REQUEST, "POINT_046", "송금 ID가 올바르지 않습니다.");
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "POINT_003", "포인트 금액은 0보다 커야 합니다."),
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "POINT_004", "사용 가능한 포인트가 부족합니다."),
    INSUFFICIENT_HELD_BALANCE(HttpStatus.CONFLICT, "POINT_005", "홀딩된 포인트가 부족합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}