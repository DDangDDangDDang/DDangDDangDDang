package com.example.ddangddangddang.domain.product.dto.response;

/**
 * 상품 이미지 한 장.
 *
 * @param key 저장소 키. 수정할 때 imageKeys·설명에 그대로 다시 보낸다.
 * @param url 화면에서 보여 줄 주소
 */
public record ProductImageResponse(String key, String url) {
}
