package com.example.ddangddangddang.domain.order.exception;

import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_001", "주문을 찾을 수 없습니다."),
    ORDER_ALREADY_EXISTS(HttpStatus.CONFLICT, "ORDER_002", "이미 주문이 생성된 경매입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}