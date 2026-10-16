package com.example.ddangddangddang.domain.directmessage.dto;

import jakarta.validation.constraints.NotNull;

public record DmRoomCreateRequest(
        @NotNull(message = "대화 상대를 정해 주세요.")
        Long targetUserId
) {
}
