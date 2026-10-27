package com.example.ddangddangddang.domain.category.exception;

import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CategoryErrorCode implements ErrorCode {

    NOT_FOUND(HttpStatus.NOT_FOUND, "CATEGORY_001", "카테고리가 존재하지 않습니다."),
    PARENT_NOT_TOP_LEVEL(HttpStatus.BAD_REQUEST, "CATEGORY_002", "소분류 아래에는 카테고리를 만들 수 없습니다."),
    DUPLICATE_NAME(HttpStatus.CONFLICT, "CATEGORY_003", "이미 같은 이름의 카테고리가 있습니다."),
    HAS_CHILDREN(HttpStatus.CONFLICT, "CATEGORY_004", "하위 카테고리가 있어 삭제 할수가 없습니다."),
    HAS_PRODUCTS(HttpStatus.CONFLICT, "CATEGORY_005", "카테고리 안에 상품이 있어 삭제를 할수가 없습니다."),
    PARENT_HAS_PRODUCTS(HttpStatus.CONFLICT, "CATEGORY_006", "상품 등록된 카테고리 안에는 하위 카테고리를 만들수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}