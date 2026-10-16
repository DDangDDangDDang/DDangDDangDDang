package com.example.ddangddangddang.domain.order.exception;

import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_001", "주문을 찾을 수 없습니다."),
    ORDER_ALREADY_EXISTS(HttpStatus.CONFLICT, "ORDER_002", "이미 주문이 생성된 경매입니다."),
    NOT_ORDER_PARTICIPANT(HttpStatus.FORBIDDEN, "ORDER_003", "주문의 구매자 또는 판매자만 조회할 수 있습니다."),
    NOT_ORDER_SELLER(HttpStatus.FORBIDDEN, "ORDER_004", "판매자만 발송 처리할 수 있습니다."),
    NOT_ORDER_BUYER(HttpStatus.FORBIDDEN, "ORDER_005", "구매자만 구매 확정할 수 있습니다."),
    CANNOT_SHIP(HttpStatus.CONFLICT, "ORDER_006", "발송 대기 중인 주문만 발송 처리할 수 있습니다."),
    CANNOT_CONFIRM(HttpStatus.CONFLICT, "ORDER_007", "배송 중인 주문만 구매 확정할 수 있습니다."),
    NOT_ORDER_BUYER_ADDRESS(HttpStatus.FORBIDDEN, "ORDER_008", "구매자만 배송지를 입력할 수 있습니다."),
    CANNOT_REGISTER_ADDRESS(HttpStatus.CONFLICT, "ORDER_009", "발송 전 주문만 배송지를 입력할 수 있습니다."),
    ADDRESS_REQUIRED(HttpStatus.CONFLICT, "ORDER_010", "구매자가 배송지를 입력한 뒤에 발송할 수 있습니다."),
    DUPLICATE_TRACKING_NO(HttpStatus.CONFLICT, "ORDER_011", "이미 다른 주문에 등록된 송장입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}