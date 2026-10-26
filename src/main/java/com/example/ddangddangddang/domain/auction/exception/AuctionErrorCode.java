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
    ON_OTHER_LIVE(HttpStatus.CONFLICT,"AUCTION_011","다른 방송에 올라가 있는 경매입니다"),

    // ===== 입찰 · 즉시구매 =====
    AUCTION_NOT_ONGOING(HttpStatus.CONFLICT, "AUCTION_012", "진행 중인 경매가 아닙니다."),
    AUCTION_TIME_OVER(HttpStatus.CONFLICT, "AUCTION_013", "경매 마감 시간이 지났습니다."),
    SELLER_CANNOT_BID(HttpStatus.FORBIDDEN, "AUCTION_014", "본인 경매에는 입찰하거나 구매할 수 없습니다."),
    ALREADY_HIGHEST_BIDDER(HttpStatus.CONFLICT, "AUCTION_015", "이미 최고 입찰자입니다."),
    BID_TOO_LOW(HttpStatus.BAD_REQUEST, "AUCTION_016", "입찰가가 최소 입찰가보다 낮습니다."),
    BID_REACHES_BUY_NOW(HttpStatus.BAD_REQUEST, "AUCTION_017", "입찰가가 즉시구매가 이상입니다. 즉시구매를 이용해주세요."),
    BID_CONFLICT(HttpStatus.CONFLICT, "AUCTION_018", "입찰이 몰리고 있습니다. 잠시 후 다시 시도해주세요."),
    BUY_NOW_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "AUCTION_019", "즉시구매가 없는 경매입니다."),
    BID_UNIT_MISMATCH(HttpStatus.BAD_REQUEST, "AUCTION_020", "입찰 금액은 입찰 단위만큼씩 올려야 합니다.");



    private final HttpStatus status;
    private final String code;
    private final String message;
}