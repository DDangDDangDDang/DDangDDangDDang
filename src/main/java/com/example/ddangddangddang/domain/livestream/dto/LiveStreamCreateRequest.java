package com.example.ddangddangddang.domain.livestream.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LiveStreamCreateRequest(
        @NotBlank(message = "방송 제목은 필수입니다.")
        @Size(max = 100, message = "방송 제목은 100자 이하입니다.")
        String title
) {
}
