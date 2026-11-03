package com.example.ddangddangddang.domain.livebot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 라이브 봇 만들기. nickname은 방송 카드·시청 화면에 판매자 이름으로 보인다. videoKey는 업로드 URL로 영상을 올린 뒤 받은 키 */
public record LiveBotCreateRequest(
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 10, message = "닉네임은 2~10자여야 합니다.")
        String nickname,

        @NotBlank(message = "녹화 영상은 필수입니다.")
        String videoKey
) {
}
