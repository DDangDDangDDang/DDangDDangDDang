package com.example.ddangddangddang.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetRequest(

        @NotBlank(message = "인증이 필요합니다.")
        String verificationToken,

        @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Pattern(regexp = PasswordFormat.REGEX, message = PasswordFormat.MESSAGE)
        String newPassword
) {
}
