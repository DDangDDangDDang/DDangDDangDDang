package com.example.ddangddangddang.domain.user.dto;

import com.example.ddangddangddang.domain.auth.dto.PhoneNumberFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 새 휴대폰 번호로 받은 인증번호를 확인하고 번호를 바꾼다 */
public record PhoneNumberChangeRequest(

        @NotBlank(message = "휴대폰 번호는 필수입니다.")
        @Pattern(regexp = PhoneNumberFormat.REGEX, message = PhoneNumberFormat.MESSAGE)
        String phoneNumber,

        @NotBlank(message = "인증번호는 필수입니다.")
        @Pattern(regexp = "^\\d{6}$", message = "인증번호는 6자리 숫자입니다.")
        String code
) {
}
