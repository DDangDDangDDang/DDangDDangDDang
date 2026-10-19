package com.example.ddangddangddang.domain.directmessage.dto;

/**
 * /user/queue/dm 으로 보내는 실시간 이벤트.
 *
 * MESSAGE: 새 메시지. message에 메시지가 담긴다. (상대에게, 그리고 보낸 사람의 다른 화면에도)
 * READ: 누군가 대화방을 읽음. readerId가 lastReadMessageId까지 읽었다. 보낸 사람 화면은 이 값으로 "1"(안 읽음)을 지운다.
 */
public record DmEvent(
        Type type,
        Long roomId,
        DmMessageResponse message,
        Long readerId,
        Long lastReadMessageId
) {
    public enum Type { MESSAGE, READ }

    public static DmEvent message(DmMessageResponse message) {
        return new DmEvent(Type.MESSAGE, message.roomId(), message, null, null);
    }

    public static DmEvent read(Long roomId, Long readerId, Long lastReadMessageId) {
        return new DmEvent(Type.READ, roomId, null, readerId, lastReadMessageId);
    }
}
