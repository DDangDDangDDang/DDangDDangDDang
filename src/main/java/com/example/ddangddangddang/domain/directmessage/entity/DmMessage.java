package com.example.ddangddangddang.domain.directmessage.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 1:1 대화방 메시지. 라이브 채팅과 달리 상대가 접속해 있지 않아도 나중에 볼 수 있도록 저장한다.
 *
 * clientMessageId: 클라이언트가 메시지마다 만드는 ID(UUID 등).
 * 응답을 못 받아 같은 메시지를 다시 보내도 한 번만 저장되도록 (보낸 사람, clientMessageId)를 유니크로 둔다.
 */
@Entity
@Table(
        name = "dm_messages",
        uniqueConstraints = @UniqueConstraint(name = "uk_dm_message_client_id", columnNames = {"sender_id", "client_message_id"}),
        // 이전 메시지 보기: 방별로 id 역순 커서 조회
        indexes = @Index(name = "idx_dm_message_room_id", columnList = "room_id, id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DmMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "client_message_id", nullable = false, length = 64)
    private String clientMessageId;

    @Column(nullable = false, length = 1000)
    private String content;

    private DmMessage(Long roomId, Long senderId, String clientMessageId, String content) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.clientMessageId = clientMessageId;
        this.content = content;
    }

    public static DmMessage create(Long roomId, Long senderId, String clientMessageId, String content) {
        return new DmMessage(roomId, senderId, clientMessageId, content);
    }
}
