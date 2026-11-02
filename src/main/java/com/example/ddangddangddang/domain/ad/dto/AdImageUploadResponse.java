package com.example.ddangddangddang.domain.ad.dto;

/**
 * 광고 이미지 업로드 URL.
 * 1. uploadUrl로 파일을 PUT 한다. (Content-Type 헤더는 요청한 형식 그대로)
 * 2. 올라가면 imageKey로 광고를 등록한다. (POST /admin/ads)
 *
 * @param imageUrl  미리보기용 주소
 * @param expiresIn uploadUrl을 쓸 수 있는 시간(초)
 */
public record AdImageUploadResponse(String uploadUrl, String imageKey, String imageUrl, long expiresIn) {
}
