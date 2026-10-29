package com.example.ddangddangddang.domain.shop.exception;

import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ShopErrorCode implements ErrorCode {

    AUCTION_NOT_BOOSTABLE(HttpStatus.BAD_REQUEST, "SHOP_001", "시작 전이거나 진행 중인 경매만 위로 올릴 수 있습니다."),
    ALREADY_BOOSTED(HttpStatus.CONFLICT, "SHOP_002", "이미 위로 올린 경매입니다. 경매 하나는 한 번만 올릴 수 있습니다."),
    BOOST_SLOTS_FULL(HttpStatus.CONFLICT, "SHOP_003", "지금은 위로 올릴 자리가 없습니다. 잠시 후 다시 시도해 주세요."),
    NOT_AUCTION_OWNER(HttpStatus.FORBIDDEN, "SHOP_004", "내 경매만 위로 올릴 수 있습니다."),
    NOT_ENOUGH_SALES(HttpStatus.BAD_REQUEST, "SHOP_005", "판매 확정 20건 이상인 판매자만 파워 판매자가 될 수 있습니다."),
    ALREADY_POWER_SELLER(HttpStatus.CONFLICT, "SHOP_006", "이미 파워 판매자입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
