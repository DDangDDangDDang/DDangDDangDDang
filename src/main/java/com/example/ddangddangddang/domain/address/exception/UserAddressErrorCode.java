package com.example.ddangddangddang.domain.address.exception;

import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserAddressErrorCode implements ErrorCode {

   ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND,"ADDRESS_001","배송지가 존재하지 않습니다."),
   NOT_ADDRESS_OWNER(HttpStatus.FORBIDDEN,"ADDRESS_002","본인 배송지만 수정,삭제 가능합니다."),
   ADDRESS_LIMIT_EXCEEDED(HttpStatus.CONFLICT,"ADDRESS_003","배송지는 최대 10개까지 저장가능합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}