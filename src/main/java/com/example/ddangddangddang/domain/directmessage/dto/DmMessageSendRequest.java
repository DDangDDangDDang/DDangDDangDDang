package com.example.ddangddangddang.domain.directmessage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * clientMessageId: 클라이언트가 메시지마다 새로 만드는 ID(UUID 권장).
 * 응답을 받지 못해 다시 보낼 때는 같은 값을 보내야 한 번만 저장된다.
 */
public record DmMessageSendRequest(
        @NotBlank(message = "메시지 ID는 필수입니다.")
        @Size(max = 64, message = "메시지 ID는 64자 이하입니다.")
        String clientMessageId,

        @NotBlank(message = "메시지를 입력해 주세요.")
        @Size(max = 1000, message = "메시지는 1000자 이하입니다.")
        String content
) {
}
