package com.example.ddangddangddang.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 소셜 가입의 추가 정보
public record SocialSignupRequest(
        @NotBlank(message = "가입 토큰이 필요합니다.")
        String signupToken,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 10, message = "닉네임은 2~10자여야 합니다.")
        String nickname,

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
        String email,

        @NotBlank(message = "휴대폰 번호는 필수입니다.")
        @Pattern(regexp = PhoneNumberFormat.REGEX, message = PhoneNumberFormat.MESSAGE)
        String phoneNumber,

        @NotBlank(message = "휴대폰 인증이 필요합니다.")
        String phoneVerificationToken
) {
}