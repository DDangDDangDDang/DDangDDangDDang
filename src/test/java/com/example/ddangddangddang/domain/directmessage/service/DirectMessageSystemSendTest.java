package com.example.ddangddangddang.domain.directmessage.service;

import com.example.ddangddangddang.domain.directmessage.entity.DmMessage;
import com.example.ddangddangddang.domain.directmessage.entity.DmRoom;
import com.example.ddangddangddang.domain.directmessage.moderation.DmModerationQueue;
import com.example.ddangddangddang.domain.directmessage.repository.DmBlockRepository;
import com.example.ddangddangddang.domain.directmessage.repository.DmMessageRepository;
import com.example.ddangddangddang.domain.directmessage.repository.DmRateLimitRepository;
import com.example.ddangddangddang.domain.directmessage.repository.DmRoomRepository;
import com.example.ddangddangddang.domain.livechat.moderation.ChatContentFilter;
import com.example.ddangddangddang.domain.seller.service.SellerVerification;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DirectMessageSystemSendTest {

    private static final Long SELLER = 1L;
    private static final Long BUYER = 2L;
    private static final Long ROOM_ID = 10L;
    private static final String CLIENT_ID = "order-delay-request-7";

    @Mock private DmRoomRepository dmRoomRepository;
    @Mock private DmMessageRepository dmMessageRepository;
    @Mock private DmBlockRepository dmBlockRepository;
    @Mock private DmRateLimitRepository dmRateLimitRepository;
    @Mock private UserRepository userRepository;
    @Mock private ChatContentFilter contentFilter;
    @Mock private StompBroadcaster stompBroadcaster;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private SellerVerification sellerVerification;
    @Mock private DmModerationQueue dmModerationQueue;

    private DirectMessageService service;
    private DmRoom room;

    @BeforeEach
    void setUp() {
        service = new DirectMessageService(dmRoomRepository, dmMessageRepository, dmBlockRepository,
                dmRateLimitRepository, userRepository, contentFilter, stompBroadcaster, transactionTemplate, sellerVerification, dmModerationQueue);
        given(transactionTemplate.execute(any())).willAnswer(invocation ->
                invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        room = DmRoom.create(SELLER, BUYER);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        given(dmRoomRepository.findByUser1IdAndUser2Id(SELLER, BUYER)).willReturn(Optional.of(room));
        given(dmMessageRepository.findBySenderIdAndClientMessageId(SELLER, CLIENT_ID)).willReturn(Optional.empty());
        given(dmMessageRepository.saveAndFlush(any(DmMessage.class))).willAnswer(call -> {
            DmMessage message = call.getArgument(0);
            ReflectionTestUtils.setField(message, "id", 30L);
            return message;
        });
    }

    @Test
    @DisplayName("구매자가 판매자를 차단했어도, 도배 한도를 넘었어도 안내는 저장되고 양쪽에 전달된다")
    void sendOrderNotice_savesEvenWhenBlocked() {
        given(dmBlockRepository.existsByBlockerIdAndBlockedId(anyLong(), anyLong())).willReturn(true);
        given(dmRateLimitRepository.increment(anyLong(), any())).willReturn(1_000L);

        service.sendOrderNotice(SELLER, BUYER, CLIENT_ID, "안내");

        then(dmMessageRepository).should().saveAndFlush(any(DmMessage.class));
        then(stompBroadcaster).should().sendToUser(eq(BUYER), eq("/queue/dm"), any());
        then(stompBroadcaster).should().sendToUser(eq(SELLER), eq("/queue/dm"), any());
    }

    @Test
    @DisplayName("도배 횟수에 세지 않고 AI 검토에도 보내지 않는다")
    void sendOrderNotice_ignoresRateLimit() {
        service.sendOrderNotice(SELLER, BUYER, CLIENT_ID, "안내");

        then(dmRateLimitRepository).should(never()).increment(anyLong(), any());
        then(dmModerationQueue).should(never()).enqueue(any());
    }

    @Test
    @DisplayName("같은 clientMessageId로 다시 호출하면 저장하지 않는다")
    void sendOrderNotice_sameClientMessageId_savesOnce() {
        given(dmMessageRepository.findBySenderIdAndClientMessageId(SELLER, CLIENT_ID))
                .willReturn(Optional.of(DmMessage.create(ROOM_ID, SELLER, CLIENT_ID, "안내")));

        service.sendOrderNotice(SELLER, BUYER, CLIENT_ID, "안내");

        then(dmMessageRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("두 사람의 대화방이 없으면 만들어서 보낸다")
    void sendOrderNotice_createsRoomWhenMissing() {
        given(dmRoomRepository.findByUser1IdAndUser2Id(SELLER, BUYER)).willReturn(Optional.empty());
        given(dmRoomRepository.saveAndFlush(any(DmRoom.class))).willReturn(room);

        service.sendOrderNotice(SELLER, BUYER, CLIENT_ID, "안내");

        then(dmRoomRepository).should().saveAndFlush(any(DmRoom.class));
        then(dmMessageRepository).should().saveAndFlush(any(DmMessage.class));
    }
}
