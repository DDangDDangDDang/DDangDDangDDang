package com.example.ddangddangddang.domain.hammer.exception;

import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SanctionErrorCode implements ErrorCode {

    BID_BANNED(HttpStatus.FORBIDDEN, "SANCTION_001", "배송지 미입력 낙찰 취소가 반복되어 입찰·즉시 구매가 정지된 상태입니다."),
    LISTING_BANNED(HttpStatus.FORBIDDEN, "SANCTION_002", "미발송 판매 취소가 반복되어 경매 등록이 정지된 상태입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
