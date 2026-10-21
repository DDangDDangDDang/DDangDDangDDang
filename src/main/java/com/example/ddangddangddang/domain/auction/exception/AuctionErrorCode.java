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
    INVALID_BUY_NOW_PRICE(HttpStatus.BAD_REQUEST, "AUCTION_005", "즉시구매가는 시작가보다 커야 합니다"),
    NOT_OWNER(HttpStatus.FORBIDDEN,"AUCTION_006","본인 경매만 취소하거나 방송에 올릴 수 있습니다"),
    NOT_CANCELABLE_STATUS(HttpStatus.CONFLICT,"AUCTION_007","대기 또는 진행 중인 경매만 취소가 가능합니다"),
    HAS_BIDS(HttpStatus.CONFLICT,"AUCTION_008","입찰이 있는 경매는 취소를 할수 없습니다"),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT,"AUCTION_009","경매 정보가 변경되었습니다. 최신 상태로 다시 확인해주세요"),
    NOT_SHOWABLE_STATUS(HttpStatus.CONFLICT,"AUCTION_010","시작 전이거나 진행 중인 경매만 방송에 올릴 수 있습니다"),
    ON_OTHER_LIVE(HttpStatus.CONFLICT,"AUCTION_011","다른 방송에 올라가 있는 경매입니다");



    private final HttpStatus status;
    private final String code;
    private final String message;
}