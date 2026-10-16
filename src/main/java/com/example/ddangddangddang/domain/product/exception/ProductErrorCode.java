package com.example.ddangddangddang.domain.product.exception;

import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {

   NOT_FOUND(HttpStatus.NOT_FOUND,"PRODUCT_001","상품이 존재하지 않습니다."),
   NOT_OWNER(HttpStatus.FORBIDDEN,"PRODUCT_002","본인 상품만 수정,삭제가 가능합니다."),
   SUBCATEGORY_REQUIRED(HttpStatus.BAD_REQUEST,"PRODUCT_003","하위 카테고리로 선택해주세요"),
   IN_AUCTION(HttpStatus.CONFLICT,"PRODUCT_004","경매 상태가 대기 OR 진행 중인 상품은 수정,삭제 불가능합니다.");


    private final HttpStatus status;
    private final String code;
    private final String message;
}