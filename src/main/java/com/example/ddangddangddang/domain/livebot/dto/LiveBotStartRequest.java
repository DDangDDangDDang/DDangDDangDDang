package com.example.ddangddangddang.domain.livebot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 라이브 봇 방송 켜기. 사람 판매자의 방송 만들기와 같은 규칙이다. (LiveStreamCreateRequest) */
public record LiveBotStartRequest(
        @NotBlank(message = "방송 제목은 필수입니다.")
        @Size(max = 100, message = "방송 제목은 100자 이하입니다.")
        String title,

        // 선택 항목
        @Size(max = 1000, message = "방송 설명은 1000자 이하입니다.")
        String description,

        // 선택 항목. 방송 목록 카드에 보여 줄 해시태그 (# 없이, 5개까지)
        List<String> hashtags
) {
}
