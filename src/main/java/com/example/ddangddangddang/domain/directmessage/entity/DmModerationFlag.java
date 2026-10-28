package com.example.ddangddangddang.domain.directmessage.entity;

import com.example.ddangddangddang.domain.livechat.moderation.ModerationCategory;
import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 검토가 관리자 확인이 필요하다고 표시한 1:1 메시지. 관리자는 신고와 같이 이 표시가 있는 대화방의 메시지를 볼 수 있다.
 * 회원 신고(DmReport)와 따로 둔다. 신고는 신고한 회원이 꼭 있어야 하고, AI 표시는 신고한 사람이 없다.
 * 같은 메시지를 두 번 표시하지 않도록 message_id를 유니크로 둔다.
 */
@Entity
@Table(
        name = "dm_moderation_flags",
        uniqueConstraints = @UniqueConstraint(name = "uk_dm_moderation_flag_message", columnNames = "message_id"),
        indexes = @Index(name = "idx_dm_moderation_flag_room", columnList = "room_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DmModerationFlag extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    // 표시된 메시지를 보낸 회원
    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ModerationCategory category;

    // AI가 적은 이유 (관리자가 읽는 한 문장)
    @Column(length = 500)
    private String reason;

    private DmModerationFlag(Long roomId, Long messageId, Long senderId, ModerationCategory category, String reason) {
        this.roomId = roomId;
        this.messageId = messageId;
        this.senderId = senderId;
        this.category = category;
        this.reason = reason;
    }

    public static DmModerationFlag create(Long roomId, Long messageId, Long senderId, ModerationCategory category, String reason) {
        return new DmModerationFlag(roomId, messageId, senderId, category, reason);
    }
}
