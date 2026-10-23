package com.example.ddangddangddang.domain.donation.exception;

import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DonationErrorCode implements ErrorCode {

    INVALID_GIFT_AMOUNT(HttpStatus.BAD_REQUEST, "DONATION_001", "선물할 수 없는 포인트입니다."),
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "DONATION_002", "선물할 수 있는 도네이션 포인트가 부족합니다."),
    MONTHLY_GIFT_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "DONATION_003", "이 판매자에게 이번 달 선물할 수 있는 포인트를 넘었습니다."),
    NOT_LIVE(HttpStatus.BAD_REQUEST, "DONATION_004", "방송 중인 판매자에게만 선물할 수 있습니다."),
    SELF_GIFT(HttpStatus.BAD_REQUEST, "DONATION_005", "내 방송에는 선물할 수 없습니다."),
    OWN_SELLER_POINT(HttpStatus.BAD_REQUEST, "DONATION_006", "이 판매자와의 거래로 받은 포인트는 이 판매자에게 선물할 수 없습니다."),
    MESSAGE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "DONATION_007", "선물 메시지에 넣을 수 없는 내용이 있습니다."),
    INSUFFICIENT_RECEIVED(HttpStatus.BAD_REQUEST, "DONATION_008", "받은 도네이션 포인트가 부족합니다."),
    NOT_VERIFIED_SELLER(HttpStatus.FORBIDDEN, "DONATION_009", "판매자 인증을 받은 회원만 구매할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
