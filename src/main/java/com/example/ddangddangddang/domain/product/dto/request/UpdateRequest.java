package com.example.ddangddangddang.domain.product.dto.request;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 상품 수정. 보내지 않은(null) 값은 그대로 둔다.
 * imageKeys: 보내면 이 순서로 통째로 바꾼다. (빈 목록이면 이미지를 모두 뺀다) 빠진 이미지는 저장소에서 지운다.
 */
public record UpdateRequest(
        Long categoryId,
        @Size(max = 50) String name,
        @Size(max = 5000) String description,
        List<String> imageKeys
) {
}
