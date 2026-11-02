package com.example.ddangddangddang.domain.user.dto;

/**
 * 프로필 이미지 업로드 URL.
 * 1. uploadUrl로 파일을 PUT 한다. (Content-Type 헤더는 요청한 형식 그대로, 토큰은 붙이지 않는다)
 * 2. 올라가면 imageKey로 프로필 이미지를 바꾼다. (PUT /users/me/profile-image)
 *
 * @param expiresIn uploadUrl을 쓸 수 있는 시간(초)
 */
public record ProfileImageUploadResponse(String uploadUrl, String imageKey, long expiresIn) {
}
