package com.example.ddangddangddang.domain.directmessage.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 두 회원의 1:1 대화방. 같은 두 사람 사이에는 방이 하나만 있다.
 * 방향과 상관없이 같은 방을 찾도록 작은 회원 ID를 user1Id, 큰 회원 ID를 user2Id에 저장하고 이 조합을 유니크로 둔다.
 * 참여자가 항상 두 명이라 별도 참여자 테이블 없이 방에 두 회원 ID를 둔다.
 */
@Entity
@Table(
        name = "dm_rooms",
        uniqueConstraints = @UniqueConstraint(name = "uk_dm_room_users", columnNames = {"user1_id", "user2_id"}),
        // 내 대화방 목록(최근 대화순): user1Id 또는 user2Id로 찾고 lastMessageId로 정렬한다.
        indexes = {
                @Index(name = "idx_dm_room_user1_last_message", columnList = "user1_id, last_message_id"),
                @Index(name = "idx_dm_room_user2_last_message", columnList = "user2_id, last_message_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DmRoom extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user1_id", nullable = false)
    private Long user1Id;

    @Column(name = "user2_id", nullable = false)
    private Long user2Id;

    // 대화방 목록의 정렬과 미리보기용. 메시지가 없으면 null (목록에 나오지 않음)
    @Column(name = "last_message_id")
    private Long lastMessageId;

    @Column(name = "last_message_preview", length = 100)
    private String lastMessagePreview;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    // 각 회원이 마지막으로 읽은 메시지 ID. 이보다 큰 id의 상대 메시지가 "읽지 않은 메시지"다. 읽은 적 없으면 null
    @Column(name = "user1_last_read_message_id")
    private Long user1LastReadMessageId;

    @Column(name = "user2_last_read_message_id")
    private Long user2LastReadMessageId;

    private DmRoom(Long user1Id, Long user2Id) {
        this.user1Id = user1Id;
        this.user2Id = user2Id;
    }

    public static DmRoom create(Long userId, Long otherUserId) {
        return new DmRoom(Math.min(userId, otherUserId), Math.max(userId, otherUserId));
    }

    public boolean isMember(Long userId) {
        return user1Id.equals(userId) || user2Id.equals(userId);
    }

    public Long otherUserId(Long userId) {
        return user1Id.equals(userId) ? user2Id : user1Id;
    }

    public boolean isUser1(Long userId) {
        return user1Id.equals(userId);
    }

    public Long lastReadMessageId(Long userId) {
        return isUser1(userId) ? user1LastReadMessageId : user2LastReadMessageId;
    }
}
