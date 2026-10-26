package com.example.ddangddangddang.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// 소셜 가입 화면의 휴대폰 인증번호 요청
public record SocialPhoneCodeRequest(
        @NotBlank(message = "가입 토큰이 필요합니다.")
        String signupToken,

        @NotBlank(message = "휴대폰 번호는 필수입니다.")
        @Pattern(regexp = PhoneNumberFormat.REGEX, message = PhoneNumberFormat.MESSAGE)
        String phoneNumber
) {
}