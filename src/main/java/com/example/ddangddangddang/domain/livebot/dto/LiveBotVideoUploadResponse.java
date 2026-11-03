package com.example.ddangddangddang.domain.livebot.dto;

/**
 * 라이브 봇 녹화 영상 업로드 URL.
 * 1. uploadUrl로 파일을 PUT 한다. (Content-Type 헤더는 요청한 형식 그대로)
 * 2. 올라가면 videoKey로 봇을 만든다. (POST /admin/live-bots)
 *
 * @param videoUrl  미리보기용 주소
 * @param expiresIn uploadUrl을 쓸 수 있는 시간(초)
 */
public record LiveBotVideoUploadResponse(String uploadUrl, String videoKey, String videoUrl, long expiresIn) {
}
