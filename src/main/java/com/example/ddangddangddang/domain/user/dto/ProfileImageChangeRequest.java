package com.example.ddangddangddang.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

/** 업로드 URL로 올린 이미지를 프로필 이미지로 쓴다. imageKey는 업로드 URL을 받을 때 함께 받은 값 */
public record ProfileImageChangeRequest(

        @NotBlank(message = "이미지 키는 필수입니다.")
        String imageKey
) {
}
