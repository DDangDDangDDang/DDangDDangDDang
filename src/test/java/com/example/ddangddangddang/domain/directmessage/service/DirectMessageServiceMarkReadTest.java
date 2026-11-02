package com.example.ddangddangddang.domain.directmessage.service;

import com.example.ddangddangddang.domain.directmessage.dto.DmEvent;
import com.example.ddangddangddang.domain.directmessage.dto.DmReadResponse;
import com.example.ddangddangddang.domain.directmessage.entity.DmRoom;
import com.example.ddangddangddang.domain.seller.service.SellerVerification;
import com.example.ddangddangddang.domain.directmessage.repository.DmBlockRepository;
import com.example.ddangddangddang.domain.directmessage.repository.DmMessageRepository;
import com.example.ddangddangddang.domain.directmessage.repository.DmRateLimitRepository;
import com.example.ddangddangddang.domain.directmessage.moderation.DmModerationQueue;
import com.example.ddangddangddang.domain.directmessage.repository.DmRoomRepository;
import com.example.ddangddangddang.domain.livechat.moderation.ChatContentFilter;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * 읽음 처리 중 다른 요청이 읽음 위치를 먼저 옮긴 경우처럼, API로 재현하기 어려운 순서를 저장소 응답으로 만들어 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class DirectMessageServiceMarkReadTest {

    private static final Long ROOM_ID = 10L;
    private static final Long ALICE = 1L; // 작은 ID라 user1
    private static final Long BOB = 2L;

    @Mock
    private DmRoomRepository dmRoomRepository;
    @Mock
    private DmMessageRepository dmMessageRepository;
    @Mock
    private DmBlockRepository dmBlockRepository;
    @Mock
    private DmRateLimitRepository dmRateLimitRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ChatContentFilter contentFilter;
    @Mock
    private StompBroadcaster stompBroadcaster;
    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private SellerVerification sellerVerification;

    @Mock
    private DmModerationQueue dmModerationQueue;

    private DirectMessageService service;

    @BeforeEach
    void setUp() {
        service = new DirectMessageService(dmRoomRepository, dmMessageRepository, dmBlockRepository,
                dmRateLimitRepository, userRepository, contentFilter, stompBroadcaster, transactionTemplate, sellerVerification, dmModerationQueue);
        // 트랜잭션 없이 콜백만 실행한다.
        given(transactionTemplate.execute(any())).willAnswer(invocation ->
                invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        given(dmMessageRepository.countUnreadByRoom(eq(ALICE), eq(List.of(ROOM_ID)))).willReturn(List.of());
    }

    @Test
    @DisplayName("다른 요청이 그 사이 더 뒤까지 읽음 처리해 갱신이 0건이면, 처음 읽은 값이 아니라 저장된 읽음 위치를 응답하고 READ는 보내지 않는다")
    void respondsWithStoredPositionWhenNotUpdated() {
        // 요청 시작 때 읽은 방: 읽음 위치 없음, 마지막 메시지 30
        DmRoom stale = room(null, 30L);
        // 갱신 직전 다른 요청이 30까지 읽음 처리해 둔 방
        DmRoom fresh = room(30L, 30L);
        given(dmRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(stale), Optional.of(fresh));
        given(dmMessageRepository.findLatestIdAtOrBefore(ROOM_ID, 20L)).willReturn(20L);
        given(dmRoomRepository.markReadByUser1(ROOM_ID, 20L)).willReturn(0);

        DmReadResponse response = service.markRead(ALICE, ROOM_ID, 20L);

        assertThat(response.lastReadMessageId()).isEqualTo(30L);
        then(stompBroadcaster).should(never()).sendToUser(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("갱신에 성공하면 그 위치를 응답하고, 상대와 나에게 READ를 보낸다")
    void respondsWithTargetWhenUpdated() {
        given(dmRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(10L, 30L)));
        given(dmRoomRepository.markReadByUser1(ROOM_ID, 30L)).willReturn(1);

        DmReadResponse response = service.markRead(ALICE, ROOM_ID, null);

        assertThat(response.lastReadMessageId()).isEqualTo(30L);
        DmEvent expected = DmEvent.read(ROOM_ID, ALICE, 30L);
        then(stompBroadcaster).should().sendToUser(BOB, "/queue/dm", expected);
        then(stompBroadcaster).should().sendToUser(ALICE, "/queue/dm", expected);
    }

    private DmRoom room(Long aliceLastRead, Long lastMessageId) {
        DmRoom room = DmRoom.create(ALICE, BOB);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        ReflectionTestUtils.setField(room, "user1LastReadMessageId", aliceLastRead);
        ReflectionTestUtils.setField(room, "lastMessageId", lastMessageId);
        return room;
    }
}
