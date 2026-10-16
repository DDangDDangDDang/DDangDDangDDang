package com.example.ddangddangddang.domain.directmessage.dto;

import com.example.ddangddangddang.domain.directmessage.entity.DmReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 신고. messageId를 보내면 그 메시지를, 보내지 않으면 대화 상대(대화 전체)를 신고한다.
 */
public record DmReportRequest(
        Long messageId,

        @NotNull(message = "신고 사유를 골라 주세요.")
        DmReportReason reason,

        @Size(max = 500, message = "신고 내용은 500자 이하입니다.")
        String detail
) {
}
