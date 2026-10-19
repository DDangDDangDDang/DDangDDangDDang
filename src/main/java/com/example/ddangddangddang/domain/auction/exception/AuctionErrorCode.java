package com.example.ddangddangddang.domain.auction.exception;

import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuctionErrorCode implements ErrorCode {

    NOT_FOUND(HttpStatus.NOT_FOUND, "AUCTION_001", "경매가 존재하지 않습니다."),
    PRODUCT_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "AUCTION_002", "판매 중인 상품만 경매에 올릴수 있습니다."),
    ALREADY_IN_AUCTION(HttpStatus.CONFLICT, "AUCTION_003", "이미 진행 OR 대기 중인 경매가 있는 상품입니다."),
    INVALID_TIME(HttpStatus.BAD_REQUEST, "AUCTION_004", "경매 종료 시간은 시작 시간 보다 지난 시간이여야 합니다."),
    INVALID_BUY_NOW_PRICE(HttpStatus.BAD_REQUEST, "AUCTION_005", "즉시구매가는 시작가보다 커야 합니다");

    private final HttpStatus status;
    private final String code;
    private final String message;
}