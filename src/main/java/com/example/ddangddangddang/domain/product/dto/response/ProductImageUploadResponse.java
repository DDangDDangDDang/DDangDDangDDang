package com.example.ddangddangddang.domain.product.dto.response;

/**
 * 상품 이미지 업로드 URL.
 * 1. uploadUrl로 파일을 PUT 한다. (Content-Type 헤더는 요청한 형식 그대로, 토큰은 붙이지 않는다)
 * 2. 올라가면 imageKey를 상품 등록·수정 요청의 imageKeys에 넣거나, 설명에 "![](imageKey)" 한 줄로 넣는다.
 *
 * @param imageUrl  올린 뒤 화면에서 보여 줄 주소 (편집 중 미리보기에 쓴다)
 * @param expiresIn uploadUrl을 쓸 수 있는 시간(초)
 */
public record ProductImageUploadResponse(String uploadUrl, String imageKey, String imageUrl, long expiresIn) {
}
