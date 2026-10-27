package com.example.ddangddangddang.domain.seller.exception;

import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SellerErrorCode implements ErrorCode {

    APPLICATION_PENDING(HttpStatus.CONFLICT, "SELLER_001", "판매자 신청을 심사하고 있습니다. 결과가 나온 뒤 다시 신청해 주세요."),
    ALREADY_VERIFIED(HttpStatus.CONFLICT, "SELLER_002", "이미 판매자 인증을 받았습니다."),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "SELLER_003", "판매자 신청을 찾을 수 없습니다."),
    ALREADY_REVIEWED(HttpStatus.CONFLICT, "SELLER_004", "이미 심사한 신청입니다."),
    CANNOT_SUBSCRIBE_SELF(HttpStatus.BAD_REQUEST, "SELLER_005", "자기 자신은 구독할 수 없습니다."),
    CONTENT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "SELLER_006", "판매자 정보에 사용할 수 없는 내용이 포함되어 있습니다."),
    INVALID_BUSINESS_NUMBER(HttpStatus.BAD_REQUEST, "SELLER_007", "사업자등록번호 10자리를 다시 확인해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
