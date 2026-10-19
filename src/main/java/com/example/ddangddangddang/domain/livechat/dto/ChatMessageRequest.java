package com.example.ddangddangddang.domain.livechat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 클라이언트가 보내는 채팅 메시지. 보낸 사람 정보는 받지 않고 서버가 인증 정보에서 채운다.
 */
public record ChatMessageRequest(
        @NotBlank(message = "메시지를 입력해 주세요.")
        @Size(max = 200, message = "메시지는 200자 이하입니다.")
        String content
) {
}
