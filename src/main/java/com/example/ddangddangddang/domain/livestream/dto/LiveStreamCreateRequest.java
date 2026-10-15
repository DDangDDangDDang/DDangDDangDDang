package com.example.ddangddangddang.domain.livestream.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LiveStreamCreateRequest(
        @NotBlank(message = "방송 제목은 필수입니다.")
        @Size(max = 100, message = "방송 제목은 100자 이하입니다.")
        String title,

        // 선택 항목
        @Size(max = 1000, message = "방송 설명은 1000자 이하입니다.")
        String description
) {
}
