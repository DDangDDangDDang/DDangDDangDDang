package com.example.ddangddangddang.domain.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 닉네임 변경권으로 바꿀 새 닉네임 (내 정보 수정과 같은 규칙) */
public record NicknameChangeTicketRequest(

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 10, message = "닉네임은 2~10자여야 합니다.")
        String nickname
) {
}
