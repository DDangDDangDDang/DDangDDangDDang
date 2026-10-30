package com.example.ddangddangddang.domain.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 상품 등록.
 * description: 글 사이에 이미지를 넣으려면 "![](이미지 키)"를 한 줄로 둔다. 그 밖의 내용은 모두 글자 그대로 보여 준다.
 * imageKeys: 상품 이미지 키(업로드 URL로 받은 키). 첫 장이 대표 이미지다. 없으면 이미지 없이 등록한다.
 */
public record ProductRequest(
        @NotNull Long categoryId,
        @NotBlank @Size(max = 50) String name,
        @Size(max = 5000) String description,
        List<String> imageKeys
) {

}
