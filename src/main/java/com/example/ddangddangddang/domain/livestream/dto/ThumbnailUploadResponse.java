package com.example.ddangddangddang.domain.livestream.dto;

/**
 * 방송 카드 썸네일 업로드 URL.
 * 1. uploadUrl로 파일을 PUT 한다. (Content-Type 헤더는 요청한 형식 그대로, 토큰은 붙이지 않는다)
 * 2. 올라가면 thumbnailKey로 썸네일을 바꾼다. (자동 캡처는 PUT /live-streams/{id}/thumbnail, 직접 올린 썸네일은 PUT /live-streams/{id}/custom-thumbnail)
 *
 * @param expiresIn uploadUrl을 쓸 수 있는 시간(초)
 */
public record ThumbnailUploadResponse(String uploadUrl, String thumbnailKey, long expiresIn) {
}
