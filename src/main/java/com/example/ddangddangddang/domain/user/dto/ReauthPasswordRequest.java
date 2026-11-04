package com.example.ddangddangddang.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

/** 이메일·휴대폰 번호를 바꾸기 전 비밀번호로 본인 확인 */
public record ReauthPasswordRequest(

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
}
