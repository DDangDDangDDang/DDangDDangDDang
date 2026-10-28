package com.example.ddangddangddang.domain.livechat.service;

import com.example.ddangddangddang.domain.livechat.dto.ChatMessageResponse;
import com.example.ddangddangddang.domain.livechat.dto.ChatMessageType;
import com.example.ddangddangddang.domain.livechat.exception.LiveChatErrorCode;
import com.example.ddangddangddang.domain.livechat.moderation.ChatContentFilter;
import com.example.ddangddangddang.domain.livechat.moderation.ChatModerationQueue;
import com.example.ddangddangddang.domain.livechat.repository.ChatRateLimitRepository;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamResponse;
import com.example.ddangddangddang.domain.livestream.entity.ChatModerationMode;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.exception.LiveStreamErrorCode;
import com.example.ddangddangddang.domain.livestream.service.LiveStreamService;
import com.example.ddangddangddang.domain.user.entity.UserRole;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.exception.CommonErrorCode;
import com.example.ddangddangddang.global.security.AuthUser;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
import com.example.ddangddangddang.domain.user.entity.NicknameBadge;
import com.example.ddangddangddang.domain.user.service.EquippedBadge;
import com.example.ddangddangddang.domain.user.service.NicknameBadges;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class LiveChatServiceTest {

    private static final Long LIVE_STREAM_ID = 10L;
    private static final Long SELLER_ID = 99L;
    private static final AuthUser SENDER = new AuthUser(1L, "user1@test.com", "회원1", UserRole.USER);

    @InjectMocks
    private LiveChatService liveChatService;

    @Mock
    private StompBroadcaster stompBroadcaster;

    @Mock
    private ChatRateLimitRepository chatRateLimitRepository;

    @Mock
    private LiveStreamService liveStreamService;

    @Mock
    private ChatContentFilter chatContentFilter;

    @Mock
    private ChatModerationQueue chatModerationQueue;

    @Mock
    private NicknameBadges nicknameBadges;

    @Test
    @DisplayName("방송 중이고 제한 횟수 이내면 앞뒤 공백을 지운 메시지를 방송 채팅 토픽으로 보내고, 같은 메시지를 AI 검토 대기열에 넣는다")
    void send() {
        given(chatRateLimitRepository.increment(SENDER.id(), LiveChatService.RATE_LIMIT_WINDOW))
                .willReturn((long) LiveChatService.RATE_LIMIT_MAX_MESSAGES);
        given(nicknameBadges.equipped(SENDER.id())).willReturn(new EquippedBadge(null));

        liveChatService.send(LIVE_STREAM_ID, SENDER, "  안녕하세요  ");

        ArgumentCaptor<ChatMessageResponse> captor = ArgumentCaptor.forClass(ChatMessageResponse.class);
        then(stompBroadcaster).should().broadcast(eq("/topic/live-streams/10/chat"), captor.capture());
        ChatMessageResponse message = captor.getValue();
        assertThat(message.messageId()).isNotBlank();
        assertThat(message.type()).isEqualTo(ChatMessageType.CHAT);
        assertThat(message.senderId()).isEqualTo(1L);
        assertThat(message.nickname()).isEqualTo("회원1");
        assertThat(message.content()).isEqualTo("안녕하세요");
        then(chatContentFilter).should().validate("안녕하세요");
        then(chatModerationQueue).should().enqueue(message);
    }

    @Test
    @DisplayName("보낸 사람이 장착한 닉네임 뱃지를 채팅 메시지에 담는다")
    void sendWithBadge() {
        given(chatRateLimitRepository.increment(SENDER.id(), LiveChatService.RATE_LIMIT_WINDOW)).willReturn(1L);
        given(nicknameBadges.equipped(SENDER.id())).willReturn(new EquippedBadge(NicknameBadge.ANGEL_WINGS));

        liveChatService.send(LIVE_STREAM_ID, SENDER, "안녕하세요");

        ArgumentCaptor<ChatMessageResponse> captor = ArgumentCaptor.forClass(ChatMessageResponse.class);
        then(stompBroadcaster).should().broadcast(eq("/topic/live-streams/10/chat"), captor.capture());
        assertThat(captor.getValue().badge()).isEqualTo(NicknameBadge.ANGEL_WINGS);
    }

    @Test
    @DisplayName("규칙 검사에 걸리면 LIVECHAT_003, 방송 상태는 조회하지 않고 메시지도 보내지 않으며 AI 검토에도 넣지 않는다")
    void contentNotAllowed() {
        given(chatRateLimitRepository.increment(SENDER.id(), LiveChatService.RATE_LIMIT_WINDOW)).willReturn(1L);
        willThrow(new BusinessException(LiveChatErrorCode.CONTENT_NOT_ALLOWED, "전화번호는 채팅으로 보낼 수 없습니다."))
                .given(chatContentFilter).validate("010-1234-5678");

        assertThatThrownBy(() -> liveChatService.send(LIVE_STREAM_ID, SENDER, "010-1234-5678"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("전화번호는 채팅으로 보낼 수 없습니다.");

        then(liveStreamService).should(never()).validateLive(any());
        then(stompBroadcaster).should(never()).broadcast(anyString(), any());
        then(chatModerationQueue).should(never()).enqueue(any());
    }

    @Test
    @DisplayName("제한 횟수를 넘으면 30초 채팅 금지 후 LIVECHAT_001, 방송 상태는 조회하지 않고 메시지도 보내지 않는다")
    void tooManyMessages() {
        given(chatRateLimitRepository.increment(SENDER.id(), LiveChatService.RATE_LIMIT_WINDOW))
                .willReturn(LiveChatService.RATE_LIMIT_MAX_MESSAGES + 1L);

        assertThatThrownBy(() -> liveChatService.send(LIVE_STREAM_ID, SENDER, "도배"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveChatErrorCode.TOO_MANY_MESSAGES);

        then(chatRateLimitRepository).should().mute(SENDER.id(), Duration.ofSeconds(30));
        then(liveStreamService).should(never()).validateLive(any());
        then(stompBroadcaster).should(never()).broadcast(anyString(), any());
    }

    @Test
    @DisplayName("채팅 금지 중이면 남은 시간을 담은 LIVECHAT_002, 횟수를 세지 않고 금지 시간도 늘리지 않는다")
    void muted() {
        given(chatRateLimitRepository.getMuteRemaining(SENDER.id())).willReturn(Duration.ofMillis(23_400));

        assertThatThrownBy(() -> liveChatService.send(LIVE_STREAM_ID, SENDER, "안녕하세요"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("도배로 채팅이 금지된 상태입니다. 24초 후에 다시 보낼 수 있습니다.")
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveChatErrorCode.CHAT_MUTED);

        then(chatRateLimitRepository).should(never()).increment(anyLong(), any());
        then(chatRateLimitRepository).should(never()).mute(anyLong(), any());
        then(liveStreamService).should(never()).validateLive(any());
        then(stompBroadcaster).should(never()).broadcast(anyString(), any());
    }

    @Test
    @DisplayName("남은 시간은 초 단위로 올림해서 1초 미만이어도 0초로 보이지 않는다")
    void mutedMessageRoundsUp() {
        assertThat(LiveChatService.mutedMessage(Duration.ofMillis(1)))
                .isEqualTo("도배로 채팅이 금지된 상태입니다. 1초 후에 다시 보낼 수 있습니다.");
        assertThat(LiveChatService.mutedMessage(Duration.ofSeconds(30)))
                .isEqualTo("도배로 채팅이 금지된 상태입니다. 30초 후에 다시 보낼 수 있습니다.");
    }

    @Test
    @DisplayName("제한 횟수 이내면 채팅 금지를 걸지 않는다")
    void withinLimitNotMuted() {
        given(chatRateLimitRepository.increment(SENDER.id(), LiveChatService.RATE_LIMIT_WINDOW))
                .willReturn((long) LiveChatService.RATE_LIMIT_MAX_MESSAGES);
        given(nicknameBadges.equipped(SENDER.id())).willReturn(new EquippedBadge(null));

        liveChatService.send(LIVE_STREAM_ID, SENDER, "안녕하세요");

        then(chatRateLimitRepository).should(never()).mute(anyLong(), any());
    }

    @Test
    @DisplayName("방송 중이 아니면 LIVESTREAM_004, 메시지를 보내지 않는다")
    void notLive() {
        given(chatRateLimitRepository.increment(SENDER.id(), LiveChatService.RATE_LIMIT_WINDOW)).willReturn(1L);
        willThrow(new BusinessException(LiveStreamErrorCode.NOT_LIVE)).given(liveStreamService).validateLive(LIVE_STREAM_ID);

        assertThatThrownBy(() -> liveChatService.send(LIVE_STREAM_ID, SENDER, "안녕하세요"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.NOT_LIVE);

        then(stompBroadcaster).should(never()).broadcast(anyString(), any());
    }

    @Test
    @DisplayName("같은 내용을 연달아 2번 넘게 보내면 LIVECHAT_004, 띄어쓰기·대소문자만 다른 문장도 같은 내용으로 센다")
    void repeatedMessage() {
        given(chatRateLimitRepository.increment(SENDER.id(), LiveChatService.RATE_LIMIT_WINDOW)).willReturn(1L);
        given(chatRateLimitRepository.countRepeated(SENDER.id(), "ㅋㅋㅋㅋok", LiveChatService.REPEAT_WINDOW))
                .willReturn(LiveChatService.MAX_REPEATED_MESSAGES + 1L);

        assertThatThrownBy(() -> liveChatService.send(LIVE_STREAM_ID, SENDER, "ㅋㅋ ㅋㅋ OK"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveChatErrorCode.REPEATED_MESSAGE);

        then(liveStreamService).should(never()).validateLive(any());
        then(stompBroadcaster).should(never()).broadcast(anyString(), any());
    }

    @Test
    @DisplayName("선물 안내는 방송 채팅 토픽으로 보내고, 한마디를 AI 검토 대기열에 넣는다")
    void sendDonationMessage() {
        liveChatService.sendDonationMessage(LIVE_STREAM_ID, 1L, "회원1", 1000, "응원해요");

        ArgumentCaptor<ChatMessageResponse> captor = ArgumentCaptor.forClass(ChatMessageResponse.class);
        then(stompBroadcaster).should().broadcast(eq("/topic/live-streams/10/chat"), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(ChatMessageType.DONATION);
        then(chatModerationQueue).should().enqueue(captor.getValue());
    }

    @Test
    @DisplayName("판매자가 가려진 메시지를 되돌리면 시청자에게 MESSAGE_RESTORED를 보낸다")
    void restoreHiddenMessage() {
        given(liveStreamService.get(LIVE_STREAM_ID)).willReturn(liveStream(SELLER_ID));
        String messageId = UUID.randomUUID().toString();

        liveChatService.restoreHiddenMessage(LIVE_STREAM_ID, SELLER_ID, messageId);

        ArgumentCaptor<ChatMessageResponse> captor = ArgumentCaptor.forClass(ChatMessageResponse.class);
        then(stompBroadcaster).should().broadcast(eq("/topic/live-streams/10/chat"), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(ChatMessageType.MESSAGE_RESTORED);
        assertThat(captor.getValue().targetMessageId()).isEqualTo(messageId);
    }

    @Test
    @DisplayName("다른 판매자의 방송이면 LIVECHAT_005, 되돌림을 보내지 않는다")
    void restoreNotOwner() {
        given(liveStreamService.get(LIVE_STREAM_ID)).willReturn(liveStream(SELLER_ID));

        assertThatThrownBy(() -> liveChatService.restoreHiddenMessage(LIVE_STREAM_ID, 999L, UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveChatErrorCode.NOT_STREAM_OWNER);

        then(stompBroadcaster).should(never()).broadcast(anyString(), any());
    }

    @Test
    @DisplayName("메시지 ID가 UUID 형식이 아니면 COMMON_001, 방송을 조회하지 않는다")
    void restoreInvalidMessageId() {
        assertThatThrownBy(() -> liveChatService.restoreHiddenMessage(LIVE_STREAM_ID, SELLER_ID, "<script>"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT);

        then(liveStreamService).should(never()).get(any());
    }

    private LiveStreamResponse liveStream(Long sellerId) {
        return new LiveStreamResponse(LIVE_STREAM_ID, sellerId, "테스트 방송", null, List.of(), null, List.of(), null, false,
                ChatModerationMode.AUTO_HIDE, LiveStreamStatus.LIVE, LocalDateTime.now(), null, LocalDateTime.now(), null);
    }

    @Test
    @DisplayName("SYSTEM 메시지는 보낸 사람 정보 없이 보낸다")
    void sendSystemMessage() {
        liveChatService.sendSystemMessage(LIVE_STREAM_ID, "방송이 종료되었습니다.");

        ArgumentCaptor<ChatMessageResponse> captor = ArgumentCaptor.forClass(ChatMessageResponse.class);
        then(stompBroadcaster).should().broadcast(eq("/topic/live-streams/10/chat"), captor.capture());
        ChatMessageResponse message = captor.getValue();
        assertThat(message.type()).isEqualTo(ChatMessageType.SYSTEM);
        assertThat(message.senderId()).isNull();
        assertThat(message.nickname()).isNull();
        assertThat(message.content()).isEqualTo("방송이 종료되었습니다.");
    }
}
