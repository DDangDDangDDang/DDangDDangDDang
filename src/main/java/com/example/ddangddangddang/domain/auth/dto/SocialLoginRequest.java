package com.example.ddangddangddang.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

// 소셜로그인에서 프론트 콜백 주소로 넘겨준 인가 코드
public record SocialLoginRequest(
        @NotBlank(message = "인가 코드가 필요합니다.")
        String code
) {
}
