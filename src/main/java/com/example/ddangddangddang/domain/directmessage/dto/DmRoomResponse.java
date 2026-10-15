package com.example.ddangddangddang.domain.directmessage.dto;

import com.example.ddangddangddang.domain.directmessage.entity.DmRoom;

import java.time.LocalDateTime;

/**
 * 대화방 정보. 요청한 회원 기준으로 상대방(otherUser)을 채운다.
 * 메시지가 아직 없으면 last* 값은 null이다.
 */
public record DmRoomResponse(
        Long roomId,
        OtherUser otherUser,
        Long lastMessageId,
        String lastMessagePreview,
        LocalDateTime lastMessageAt
) {
    public static DmRoomResponse of(DmRoom room, Long otherUserId, String otherNickname) {
        return new DmRoomResponse(room.getId(), new OtherUser(otherUserId, otherNickname),
                room.getLastMessageId(), room.getLastMessagePreview(), room.getLastMessageAt());
    }

    // 탈퇴 등으로 회원을 찾지 못하면 nickname은 null
    public record OtherUser(Long userId, String nickname) {
    }
}
