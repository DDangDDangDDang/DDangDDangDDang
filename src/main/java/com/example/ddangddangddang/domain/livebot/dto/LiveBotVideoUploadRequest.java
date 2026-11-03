package com.example.ddangddangddang.domain.livebot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 라이브 봇 녹화 영상 업로드 URL 요청. 올릴 파일의 형식과 크기(바이트)를 먼저 알려 준다. */
public record LiveBotVideoUploadRequest(
        @NotBlank(message = "파일 형식은 필수입니다.")
        String contentType,

        @NotNull(message = "파일 크기는 필수입니다.")
        @Positive(message = "빈 파일은 올릴 수 없습니다.")
        Long contentLength
) {
}
