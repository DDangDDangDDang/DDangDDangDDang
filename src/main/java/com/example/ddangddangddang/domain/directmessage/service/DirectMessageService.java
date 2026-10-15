package com.example.ddangddangddang.domain.directmessage.service;

import com.example.ddangddangddang.domain.directmessage.dto.DmEvent;
import com.example.ddangddangddang.domain.directmessage.dto.DmMessageResponse;
import com.example.ddangddangddang.domain.directmessage.dto.DmMessageSendRequest;
import com.example.ddangddangddang.domain.directmessage.dto.DmReadResponse;
import com.example.ddangddangddang.domain.directmessage.dto.DmRoomResponse;
import com.example.ddangddangddang.domain.directmessage.dto.DmUnreadCountResponse;
import com.example.ddangddangddang.domain.directmessage.entity.DmMessage;
import com.example.ddangddangddang.domain.directmessage.entity.DmRoom;
import com.example.ddangddangddang.domain.directmessage.exception.DirectMessageErrorCode;
import com.example.ddangddangddang.domain.directmessage.repository.DmBlockRepository;
import com.example.ddangddangddang.domain.directmessage.repository.DmMessageRepository;
import com.example.ddangddangddang.domain.directmessage.repository.DmMessageRepository.RoomUnreadCount;
import com.example.ddangddangddang.domain.directmessage.repository.DmRateLimitRepository;
import com.example.ddangddangddang.domain.directmessage.repository.DmRoomRepository;
import com.example.ddangddangddang.domain.livechat.moderation.ChatContentFilter;
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

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 회원 간 1:1 채팅. 아무 회원에게나 대화를 시작할 수 있다.
 *
 * 보내기·읽음은 HTTP로 받아 DB에 저장하고, 커밋된 뒤 StompBroadcaster.sendToUser로 /user/queue/dm 에 실시간 전달한다.
 * 상대가 접속해 있지 않아도 메시지는 저장되어 있어, 대화방 목록과 이전 메시지 조회로 볼 수 있다.
 * 남용은 내용 필터(연락처·계좌 등), 도배 제한, 차단, 신고로 막는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DirectMessageService {

    static final String DM_DESTINATION = "/queue/dm";
    static final int RATE_LIMIT_MAX_MESSAGES = 10;
    static final Duration RATE_LIMIT_WINDOW = Duration.ofSeconds(10);
    private static final int MAX_PAGE_SIZE = 50;
    private static final int PREVIEW_LENGTH = 100;

    private final DmRoomRepository dmRoomRepository;
    private final DmMessageRepository dmMessageRepository;
    private final DmBlockRepository dmBlockRepository;
    private final DmRateLimitRepository dmRateLimitRepository;
    private final UserRepository userRepository;
    private final ChatContentFilter contentFilter;
    private final StompBroadcaster stompBroadcaster;
    private final TransactionTemplate transactionTemplate;

    /**
     * 상대와의 대화방을 가져온다. 없으면 만든다. 차단한 상대여도 방은 열 수 있다. (이전 대화 보기, 차단 해제)
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
        return DmRoomResponse.of(room, userId, target.getNickname(), unreadCount(userId, room.getId()),
                dmBlockRepository.existsByBlockerIdAndBlockedId(userId, targetUserId));
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
     * 내 대화방 목록. 메시지가 있는 방만 최근 대화순으로, 방마다 읽지 않은 메시지 수와 함께 보여 준다.
     * cursor에는 응답의 nextCursor(마지막 방의 lastMessageId)를 넘긴다.
     */
    public CursorResponse<DmRoomResponse> getRooms(Long userId, Long cursor, int size) {
        int pageSize = pageSize(size);
        List<DmRoom> rooms = dmRoomRepository.findMyRooms(userId, cursor, PageRequest.of(0, pageSize + 1));
        if (rooms.isEmpty()) {
            return new CursorResponse<>(List.of(), null, false);
        }

        Set<Long> otherUserIds = rooms.stream().map(room -> room.otherUserId(userId)).collect(Collectors.toSet());
        Map<Long, String> nicknames = userRepository.findAllById(otherUserIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));
        Map<Long, Long> unreadCounts = dmMessageRepository
                .countUnreadByRoom(userId, rooms.stream().map(DmRoom::getId).toList()).stream()
                .collect(Collectors.toMap(RoomUnreadCount::getRoomId, RoomUnreadCount::getCount));
        Set<Long> blocked = new HashSet<>(dmBlockRepository.findBlockedIds(userId, otherUserIds));

        return CursorResponse.of(rooms, pageSize,
                room -> {
                    Long otherUserId = room.otherUserId(userId);
                    return DmRoomResponse.of(room, userId, nicknames.get(otherUserId),
                            unreadCounts.getOrDefault(room.getId(), 0L), blocked.contains(otherUserId));
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

    public DmUnreadCountResponse getUnreadCount(Long userId) {
        return new DmUnreadCountResponse(dmMessageRepository.countAllUnread(userId));
    }

    /**
     * 메시지를 저장하고, 커밋된 뒤 상대와 나에게 실시간으로 보낸다. (MESSAGE)
     * 보낸 사람은 자기 메시지까지 읽은 것으로 하고, 읽음 위치가 바뀌었으면 READ도 이어서 보낸다.
     *
     * 검사 순서: 참여자 → 같은 clientMessageId 재전송 → 내용 필터 → 차단 → 도배 제한
     * 같은 clientMessageId로 다시 보내면(응답을 못 받은 재전송) 다른 검사 없이 처음 저장한 메시지를 돌려준다.
     * 이 경우 처음 보낼 때 이미 실시간 전달을 했으므로 다시 보내지 않고, 도배 횟수에도 세지 않는다.
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

        // 1:1 채팅은 플랫폼 밖 거래(직거래·계좌 입금) 유도가 가장 많이 일어나는 곳이라 라이브 채팅과 같은 규칙으로 막는다.
        contentFilter.findViolation(content).ifPresent(violation -> {
            throw new BusinessException(DirectMessageErrorCode.CONTENT_NOT_ALLOWED, violation.getMessage());
        });
        Long otherUserId = room.otherUserId(userId);
        validateNotBlocked(userId, otherUserId);
        if (dmRateLimitRepository.increment(userId, RATE_LIMIT_WINDOW) > RATE_LIMIT_MAX_MESSAGES) {
            throw new BusinessException(DirectMessageErrorCode.TOO_MANY_MESSAGES);
        }

        SendResult result;
        try {
            result = transactionTemplate.execute(status -> {
                DmMessage message = dmMessageRepository.saveAndFlush(
                        DmMessage.create(roomId, userId, request.clientMessageId(), content));
                dmRoomRepository.updateLastMessage(roomId, message.getId(), preview(content), message.getCreatedAt());
                boolean readMoved = markReadUpTo(room, userId, message.getId()) > 0;
                return new SendResult(message, readMoved);
            });
        } catch (DataIntegrityViolationException e) {
            // 같은 메시지를 거의 동시에 두 번 보내 둘 다 위의 중복 확인을 통과한 경우. 먼저 저장된 것을 돌려준다.
            DmMessage duplicate = dmMessageRepository.findBySenderIdAndClientMessageId(userId, request.clientMessageId())
                    .orElseThrow(() -> e);
            return alreadySent(duplicate, roomId);
        }

        DmMessageResponse response = DmMessageResponse.from(result.message());
        DmEvent event = DmEvent.message(response);
        deliver(otherUserId, event);
        // 같은 계정으로 연 다른 탭·기기에도 보낸 메시지가 보이도록 나에게도 보낸다.
        deliver(userId, event);
        // 보내면서 읽음 위치가 앞으로 갔으면(그 사이 온 상대 메시지까지 읽음) 상대 화면의 "1"과 내 다른 탭의 읽지 않은 수를 갱신한다.
        // 그 사이 다른 요청이 이미 더 뒤까지 읽음 처리했다면 위치가 바뀌지 않으므로 보내지 않는다.
        if (result.readMoved()) {
            DmEvent read = DmEvent.read(roomId, userId, response.messageId());
            deliver(otherUserId, read);
            deliver(userId, read);
        }
        return response;
    }

    // 메시지 저장 트랜잭션의 결과. readMoved: 보낸 사람의 읽음 위치가 이 메시지로 바뀌었는지
    private record SendResult(DmMessage message, boolean readMoved) {
    }

    /**
     * 읽음 처리. lastReadMessageId가 없으면 방의 마지막 메시지까지 읽은 것으로 한다.
     * 읽음 위치는 앞으로만 움직인다. 위치가 바뀌었으면 상대(내 메시지의 "1"을 지우도록)와 내 다른 화면에 READ를 보낸다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DmReadResponse markRead(Long userId, Long roomId, Long lastReadMessageId) {
        DmRoom room = findRoomAsMember(userId, roomId);
        Long previous = room.lastReadMessageId(userId);
        Long lastMessageId = room.getLastMessageId();
        if (lastMessageId == null) {
            return new DmReadResponse(roomId, previous, 0);
        }
        // 읽음 위치는 항상 이 방의 실제 메시지 ID로 둔다.
        // 방에 없는 미래의 ID는 마지막 메시지에서 자르고, 다른 방의 메시지 ID는 그 이하인 이 방의 가장 최근 메시지로 맞춘다.
        Long target = lastReadMessageId == null
                ? lastMessageId
                : dmMessageRepository.findLatestIdAtOrBefore(roomId, Math.min(lastReadMessageId, lastMessageId));
        if (target == null) {
            // 이 방에 그 ID 이하의 메시지가 없다. (예: 방의 첫 메시지보다 작은 ID) 읽을 메시지가 없으므로 그대로 둔다.
            return new DmReadResponse(roomId, previous, unreadCount(userId, roomId));
        }

        Integer updated = transactionTemplate.execute(status -> markReadUpTo(room, userId, target));
        Long current = previous == null || previous < target ? target : previous;
        if (updated != null && updated > 0) {
            DmEvent event = DmEvent.read(roomId, userId, current);
            deliver(room.otherUserId(userId), event);
            deliver(userId, event);
        }
        return new DmReadResponse(roomId, current, unreadCount(userId, roomId));
    }

    private int markReadUpTo(DmRoom room, Long userId, long messageId) {
        return room.isUser1(userId)
                ? dmRoomRepository.markReadByUser1(room.getId(), messageId)
                : dmRoomRepository.markReadByUser2(room.getId(), messageId);
    }

    private void validateNotBlocked(Long userId, Long otherUserId) {
        if (dmBlockRepository.existsByBlockerIdAndBlockedId(userId, otherUserId)) {
            throw new BusinessException(DirectMessageErrorCode.BLOCKED_BY_ME);
        }
        if (dmBlockRepository.existsByBlockerIdAndBlockedId(otherUserId, userId)) {
            throw new BusinessException(DirectMessageErrorCode.BLOCKED_BY_OTHER);
        }
    }

    private long unreadCount(Long userId, Long roomId) {
        return dmMessageRepository.countUnreadByRoom(userId, List.of(roomId)).stream()
                .mapToLong(RoomUnreadCount::getCount)
                .sum();
    }

    private DmMessageResponse alreadySent(DmMessage message, Long roomId) {
        if (!message.getRoomId().equals(roomId)) {
            throw new BusinessException(DirectMessageErrorCode.CLIENT_MESSAGE_ID_CONFLICT);
        }
        return DmMessageResponse.from(message);
    }

    // 실시간 전달은 보조 수단이다. 실패해도 저장된 내용은 목록·이전 메시지 조회로 볼 수 있으므로 요청을 실패시키지 않는다.
    private void deliver(Long receiverId, DmEvent event) {
        try {
            stompBroadcaster.sendToUser(receiverId, DM_DESTINATION, event);
        } catch (RuntimeException e) {
            log.warn("1:1 채팅 실시간 전달 실패. receiverId={}, type={}, roomId={}", receiverId, event.type(), event.roomId(), e);
        }
    }

    DmRoom findRoomAsMember(Long userId, Long roomId) {
        DmRoom room = dmRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(DirectMessageErrorCode.ROOM_NOT_FOUND));
        if (!room.isMember(userId)) {
            throw new BusinessException(DirectMessageErrorCode.NOT_MEMBER);
        }
        return room;
    }

    static int pageSize(int size) {
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
