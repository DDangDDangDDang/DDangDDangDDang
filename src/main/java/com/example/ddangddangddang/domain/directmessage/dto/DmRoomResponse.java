package com.example.ddangddangddang.domain.directmessage.dto;

import com.example.ddangddangddang.domain.directmessage.entity.DmRoom;

import java.time.LocalDateTime;

/**
 * 대화방 정보. 요청한 회원 기준으로 채운다.
 * - otherUser: 상대방
 * - last*: 마지막 메시지. 메시지가 아직 없으면 null
 * - unreadCount: 상대가 보낸 메시지 중 내가 읽지 않은 수
 * - otherLastReadMessageId: 상대가 마지막으로 읽은 메시지. 내가 보낸 메시지 중 이보다 id가 크면 상대가 아직 안 읽은 것("1")
 */
public record DmRoomResponse(
        Long roomId,
        OtherUser otherUser,
        Long lastMessageId,
        String lastMessagePreview,
        LocalDateTime lastMessageAt,
        long unreadCount,
        Long otherLastReadMessageId
) {
    public static DmRoomResponse of(DmRoom room, Long userId, String otherNickname, long unreadCount) {
        Long otherUserId = room.otherUserId(userId);
        return new DmRoomResponse(room.getId(), new OtherUser(otherUserId, otherNickname),
                room.getLastMessageId(), room.getLastMessagePreview(), room.getLastMessageAt(),
                unreadCount, room.lastReadMessageId(otherUserId));
    }

    // 탈퇴 등으로 회원을 찾지 못하면 nickname은 null
    public record OtherUser(Long userId, String nickname) {
    }
}
