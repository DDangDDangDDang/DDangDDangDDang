package com.example.ddangddangddang.domain.directmessage.dto;

import com.example.ddangddangddang.domain.directmessage.entity.DmReport;
import com.example.ddangddangddang.domain.directmessage.entity.DmReportReason;

import java.time.LocalDateTime;

/**
 * 신고 정보. 관리자 목록에서는 reportedContent(신고한 메시지 내용)를 함께 보여 준다. 대화 전체 신고면 null
 */
public record DmReportResponse(
        Long reportId,
        Long reporterId,
        Long reportedUserId,
        Long roomId,
        Long messageId,
        DmReportReason reason,
        String reasonLabel,
        String detail,
        String reportedContent,
        LocalDateTime reportedAt
) {
    public static DmReportResponse of(DmReport report, String reportedContent) {
        return new DmReportResponse(report.getId(), report.getReporterId(), report.getReportedUserId(),
                report.getRoomId(), report.getMessageId(), report.getReason(), report.getReason().getLabel(),
                report.getDetail(), reportedContent, report.getCreatedAt());
    }
}
