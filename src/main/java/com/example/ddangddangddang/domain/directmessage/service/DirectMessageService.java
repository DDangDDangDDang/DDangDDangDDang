package com.example.ddangddangddang.domain.directmessage.service;

import com.example.ddangddangddang.domain.directmessage.dto.DmMessageResponse;
import com.example.ddangddangddang.domain.directmessage.dto.DmMessageSendRequest;
import com.example.ddangddangddang.domain.directmessage.dto.DmRoomResponse;
import com.example.ddangddangddang.domain.directmessage.entity.DmMessage;
import com.example.ddangddangddang.domain.directmessage.entity.DmRoom;
import com.example.ddangddangddang.domain.directmessage.exception.DirectMessageErrorCode;
import com.example.ddangddangddang.domain.directmessage.repository.DmMessageRepository;
import com.example.ddangddangddang.domain.directmessage.repository.DmRoomRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.exception.CommonErrorCode;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 회원 간 1:1 채팅. 아무 회원에게나 대화를 시작할 수 있다.
 *
 * 보내기는 HTTP로 받아 DB에 저장하고, 커밋된 뒤 StompBroadcaster.sendToUser로 상대와 내 다른 화면(탭)에 실시간 전달한다.
 * 상대가 접속해 있지 않아도 메시지는 저장되어 있어, 대화방 목록과 이전 메시지 조회로 볼 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DirectMessageService {

    static final String DM_DESTINATION = "/queue/dm";
    private static final int MAX_PAGE_SIZE = 50;
    private static final int PREVIEW_LENGTH = 100;

    private final DmRoomRepository dmRoomRepository;
    private final DmMessageRepository dmMessageRepository;
    private final UserRepository userRepository;
    private final StompBroadcaster stompBroadcaster;
    private final TransactionTemplate transactionTemplate;

    /**
     * 상대와의 대화방을 가져온다. 없으면 만든다.
     *
     * 두 사람이 동시에 서로에게 방을 만들면 유니크 제약에 한 쪽이 실패하는데, 그때는 먼저 만들어진 방을 다시 찾아 돌려준다.
     * 실패한 저장이 이 메서드의 트랜잭션을 롤백 전용으로 만들지 않도록, 저장은 TransactionTemplate으로 따로 커밋한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DmRoomResponse getOrCreateRoom(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new BusinessException(DirectMessageErrorCode.CANNOT_MESSAGE_SELF);
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(DirectMessageErrorCode.TARGET_USER_NOT_FOUND));

        DmRoom candidate = DmRoom.create(userId, targetUserId);
        DmRoom room = dmRoomRepository.findByUser1IdAndUser2Id(candidate.getUser1Id(), candidate.getUser2Id())
                .orElseGet(() -> createRoom(candidate));
        return DmRoomResponse.of(room, target.getId(), target.getNickname());
    }

    private DmRoom createRoom(DmRoom candidate) {
        try {
            return transactionTemplate.execute(status -> dmRoomRepository.saveAndFlush(candidate));
        } catch (DataIntegrityViolationException e) {
            return dmRoomRepository.findByUser1IdAndUser2Id(candidate.getUser1Id(), candidate.getUser2Id())
                    .orElseThrow(() -> e);
        }
    }

    /**
     * 내 대화방 목록. 메시지가 있는 방만 최근 대화순으로 보여 준다.
     * cursor에는 응답의 nextCursor(마지막 방의 lastMessageId)를 넘긴다.
     */
    public CursorResponse<DmRoomResponse> getRooms(Long userId, Long cursor, int size) {
        int pageSize = pageSize(size);
        List<DmRoom> rooms = dmRoomRepository.findMyRooms(userId, cursor, PageRequest.of(0, pageSize + 1));

        List<Long> otherUserIds = rooms.stream().map(room -> room.otherUserId(userId)).distinct().toList();
        Map<Long, String> nicknames = userRepository.findAllById(otherUserIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));

        return CursorResponse.of(rooms, pageSize,
                room -> {
                    Long otherUserId = room.otherUserId(userId);
                    return DmRoomResponse.of(room, otherUserId, nicknames.get(otherUserId));
                },
                DmRoom::getLastMessageId);
    }

    /**
     * 이전 메시지 보기. 최신 메시지부터 size개씩, cursor보다 오래된 메시지를 가져온다.
     */
    public CursorResponse<DmMessageResponse> getMessages(Long userId, Long roomId, Long cursor, int size) {
        findRoomAsMember(userId, roomId);
        int pageSize = pageSize(size);
        List<DmMessage> messages = dmMessageRepository.findByCursor(roomId, cursor, PageRequest.of(0, pageSize + 1));
        return CursorResponse.of(messages, pageSize, DmMessageResponse::from, DmMessage::getId);
    }

    /**
     * 메시지를 저장하고, 커밋된 뒤 상대와 나에게 실시간으로 보낸다.
     *
     * 같은 clientMessageId로 다시 보내면(응답을 못 받은 재전송) 새로 저장하지 않고 처음 저장한 메시지를 돌려준다.
     * 이 경우 처음 보낼 때 이미 실시간 전달을 했으므로 다시 보내지 않는다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DmMessageResponse send(Long userId, Long roomId, DmMessageSendRequest request) {
        String content = request.content().strip();
        if (content.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "메시지를 입력해 주세요.");
        }
        DmRoom room = findRoomAsMember(userId, roomId);

        DmMessage existing = dmMessageRepository.findBySenderIdAndClientMessageId(userId, request.clientMessageId())
                .orElse(null);
        if (existing != null) {
            return alreadySent(existing, roomId);
        }

        DmMessage saved;
        try {
            saved = transactionTemplate.execute(status -> {
                DmMessage message = dmMessageRepository.saveAndFlush(
                        DmMessage.create(roomId, userId, request.clientMessageId(), content));
                dmRoomRepository.updateLastMessage(roomId, message.getId(), preview(content), message.getCreatedAt());
                return message;
            });
        } catch (DataIntegrityViolationException e) {
            // 같은 메시지를 거의 동시에 두 번 보내 둘 다 위의 중복 확인을 통과한 경우. 먼저 저장된 것을 돌려준다.
            DmMessage duplicate = dmMessageRepository.findBySenderIdAndClientMessageId(userId, request.clientMessageId())
                    .orElseThrow(() -> e);
            return alreadySent(duplicate, roomId);
        }

        DmMessageResponse response = DmMessageResponse.from(saved);
        deliver(room.otherUserId(userId), response);
        // 같은 계정으로 연 다른 탭·기기에도 보낸 메시지가 보이도록 나에게도 보낸다.
        deliver(userId, response);
        return response;
    }

    private DmMessageResponse alreadySent(DmMessage message, Long roomId) {
        if (!message.getRoomId().equals(roomId)) {
            throw new BusinessException(DirectMessageErrorCode.CLIENT_MESSAGE_ID_CONFLICT);
        }
        return DmMessageResponse.from(message);
    }

    // 실시간 전달은 보조 수단이다. 실패해도 메시지는 저장되어 있어 목록·이전 메시지 조회로 볼 수 있으므로 요청을 실패시키지 않는다.
    private void deliver(Long receiverId, DmMessageResponse message) {
        try {
            stompBroadcaster.sendToUser(receiverId, DM_DESTINATION, message);
        } catch (RuntimeException e) {
            log.warn("1:1 채팅 실시간 전달 실패. receiverId={}, messageId={}", receiverId, message.messageId(), e);
        }
    }

    private DmRoom findRoomAsMember(Long userId, Long roomId) {
        DmRoom room = dmRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(DirectMessageErrorCode.ROOM_NOT_FOUND));
        if (!room.isMember(userId)) {
            throw new BusinessException(DirectMessageErrorCode.NOT_MEMBER);
        }
        return room;
    }

    private static int pageSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    // 목록 미리보기. 줄바꿈은 공백으로 바꾸고, 이모지가 반으로 잘리지 않도록 글자(code point) 단위로 자른다.
    static String preview(String content) {
        String oneLine = content.replaceAll("\\s+", " ");
        if (oneLine.codePointCount(0, oneLine.length()) <= PREVIEW_LENGTH) {
            return oneLine;
        }
        return oneLine.substring(0, oneLine.offsetByCodePoints(0, PREVIEW_LENGTH));
    }
}
