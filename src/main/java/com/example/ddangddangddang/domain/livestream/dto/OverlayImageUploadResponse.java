package com.example.ddangddangddang.domain.livestream.dto;

/**
 * 방송 화면 이미지 박스용 업로드 URL.
 * 1. uploadUrl로 파일을 PUT 한다. (Content-Type 헤더는 요청한 형식 그대로, 토큰은 붙이지 않는다)
 * 2. 올라가면 imageKey를 이미지 박스에 넣어 화면 배치를 저장한다. (PUT /live-streams/{id}/overlay-layout)
 *
 * @param imageUrl  올린 뒤 화면에서 보여 줄 주소 (편집 중 미리보기에 쓴다)
 * @param expiresIn uploadUrl을 쓸 수 있는 시간(초)
 */
public record OverlayImageUploadResponse(String uploadUrl, String imageKey, String imageUrl, long expiresIn) {
}
