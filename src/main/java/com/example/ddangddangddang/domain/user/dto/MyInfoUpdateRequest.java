package com.example.ddangddangddang.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 내 정보 수정. 지금은 닉네임만 바꿀 수 있다. (이메일·휴대폰은 인증이 필요해 별도 기능으로 둔다) */
public record MyInfoUpdateRequest(

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 10, message = "닉네임은 2~10자여야 합니다.")
        String nickname
) {
}