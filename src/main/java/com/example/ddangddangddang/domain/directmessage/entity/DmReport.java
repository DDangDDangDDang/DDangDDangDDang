package com.example.ddangddangddang.domain.directmessage.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 1:1 채팅 신고. 관리자는 신고가 들어온 대화방의 메시지만 볼 수 있다.
 *
 * messageId가 있으면 특정 메시지 신고, 없으면 대화 전체(상대) 신고다.
 * 같은 메시지를 같은 사람이 여러 번 신고하지 못하도록 (reporter_id, message_id)를 유니크로 둔다.
 * (대화 전체 신고는 message_id가 null이라 유니크에 걸리지 않는다)
 */
@Entity
@Table(
        name = "dm_reports",
        uniqueConstraints = @UniqueConstraint(name = "uk_dm_report_message", columnNames = {"reporter_id", "message_id"}),
        indexes = @Index(name = "idx_dm_report_room", columnList = "room_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DmReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Column(name = "reported_user_id", nullable = false)
    private Long reportedUserId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "message_id")
    private Long messageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DmReportReason reason;

    @Column(length = 500)
    private String detail;

    private DmReport(Long reporterId, Long reportedUserId, Long roomId, Long messageId, DmReportReason reason, String detail) {
        this.reporterId = reporterId;
        this.reportedUserId = reportedUserId;
        this.roomId = roomId;
        this.messageId = messageId;
        this.reason = reason;
        this.detail = detail;
    }

    public static DmReport create(Long reporterId, Long reportedUserId, Long roomId, Long messageId,
                                  DmReportReason reason, String detail) {
        return new DmReport(reporterId, reportedUserId, roomId, messageId, reason, detail);
    }
}
