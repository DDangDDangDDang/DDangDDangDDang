package com.example.ddangddangddang.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneCodeRequest(

        @NotBlank(message = "휴대폰 번호는 필수입니다.")
        @Pattern(regexp = PhoneNumberFormat.REGEX, message = PhoneNumberFormat.MESSAGE)
        String phoneNumber
) {
}