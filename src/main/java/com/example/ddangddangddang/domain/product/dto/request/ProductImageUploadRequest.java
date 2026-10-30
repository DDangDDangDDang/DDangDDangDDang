package com.example.ddangddangddang.domain.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 상품 이미지(갤러리·설명 속 이미지) 업로드 URL 요청. 올릴 파일의 형식과 크기(바이트)를 먼저 알려 준다.
 * 업로드 URL은 이 형식·크기의 파일만 받는다.
 */
public record ProductImageUploadRequest(

        @NotBlank(message = "파일 형식은 필수입니다.")
        String contentType,

        @NotNull(message = "파일 크기는 필수입니다.")
        @Positive(message = "빈 파일은 올릴 수 없습니다.")
        Long contentLength
) {
}
