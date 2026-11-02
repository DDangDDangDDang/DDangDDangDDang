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
   IN_AUCTION(HttpStatus.CONFLICT,"PRODUCT_004","경매 상태가 대기 OR 진행 중인 상품은 수정,삭제 불가능합니다."),
   NOT_OWNER_AUCTION_HISTORY(HttpStatus.FORBIDDEN,"PRODUCT_005","본인 상품의 경매 이력만 볼수 있습니다."),
   UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST,"PRODUCT_006","상품 이미지는 JPG, PNG, WEBP, GIF만 올릴 수 있습니다."),
   IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST,"PRODUCT_007","상품 이미지는 10MB 이하만 올릴 수 있습니다."),
   IMAGE_NOT_UPLOADED(HttpStatus.BAD_REQUEST,"PRODUCT_008","올라가지 않았거나 쓸 수 없는 상품 이미지입니다. 다시 올려 주세요."),
   TOO_MANY_IMAGES(HttpStatus.BAD_REQUEST,"PRODUCT_009","상품 이미지는 10장까지 올릴 수 있습니다."),
   TOO_MANY_DESCRIPTION_IMAGES(HttpStatus.BAD_REQUEST,"PRODUCT_010","상품 설명에는 이미지를 20장까지 넣을 수 있습니다.");


    private final HttpStatus status;
    private final String code;
    private final String message;
}