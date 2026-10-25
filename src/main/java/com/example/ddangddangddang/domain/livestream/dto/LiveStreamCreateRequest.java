package com.example.ddangddangddang.domain.livestream.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record LiveStreamCreateRequest(
        @NotBlank(message = "방송 제목은 필수입니다.")
        @Size(max = 100, message = "방송 제목은 100자 이하입니다.")
        String title,

        // 선택 항목
        @Size(max = 1000, message = "방송 설명은 1000자 이하입니다.")
        String description,

        // 선택 항목. 방송 시작 예정 시간 (예: "2026-10-20T20:00"). 지금부터 30일 이내. 없으면 바로 시작하는 방송
        LocalDateTime scheduledStartAt,

        // 선택 항목. 방송 목록 카드에 보여 줄 해시태그 (# 없이, 5개까지, 한 개에 한글·영문·숫자·밑줄 20자까지). 앞의 #은 빼고 저장한다.
        List<String> hashtags
) {
    public LiveStreamCreateRequest(String title, String description) {
        this(title, description, null);
    }

    public LiveStreamCreateRequest(String title, String description, LocalDateTime scheduledStartAt) {
        this(title, description, scheduledStartAt, null);
    }
}
