package com.example.ddangddangddang.domain.directmessage.dto;

import jakarta.validation.constraints.NotNull;

public record DmBlockRequest(
        @NotNull(message = "차단할 회원을 정해 주세요.")
        Long userId
) {
}
