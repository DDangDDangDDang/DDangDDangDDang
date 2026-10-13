package com.example.ddangddangddang.domain.livechat.service;

import com.example.ddangddangddang.domain.livechat.dto.ChatMessageResponse;
import com.example.ddangddangddang.domain.livechat.dto.ChatMessageType;
import com.example.ddangddangddang.domain.livechat.exception.LiveChatErrorCode;
import com.example.ddangddangddang.domain.livechat.repository.ChatRateLimitRepository;
import com.example.ddangddangddang.domain.livestream.exception.LiveStreamErrorCode;
import com.example.ddangddangddang.domain.livestream.service.LiveStreamService;
import com.example.ddangddangddang.domain.user.entity.UserRole;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.security.AuthUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;

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
    private static final AuthUser SENDER = new AuthUser(1L, "user1@test.com", "회원1", UserRole.USER);

    @InjectMocks
    private LiveChatService liveChatService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ChatRateLimitRepository chatRateLimitRepository;

    @Mock
    private LiveStreamService liveStreamService;

    @Test
    @DisplayName("방송 중이고 제한 횟수 이내면 앞뒤 공백을 지운 메시지를 방송 채팅 토픽으로 보낸다")
    void send() {
        given(chatRateLimitRepository.increment(SENDER.id(), LiveChatService.RATE_LIMIT_WINDOW))
                .willReturn((long) LiveChatService.RATE_LIMIT_MAX_MESSAGES);

        liveChatService.send(LIVE_STREAM_ID, SENDER, "  안녕하세요  ");

        ArgumentCaptor<ChatMessageResponse> captor = ArgumentCaptor.forClass(ChatMessageResponse.class);
        then(messagingTemplate).should().convertAndSend(eq("/topic/live-streams/10/chat"), captor.capture());
        ChatMessageResponse message = captor.getValue();
        assertThat(message.type()).isEqualTo(ChatMessageType.CHAT);
        assertThat(message.senderId()).isEqualTo(1L);
        assertThat(message.nickname()).isEqualTo("회원1");
        assertThat(message.content()).isEqualTo("안녕하세요");
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
        then(messagingTemplate).should(never()).convertAndSend(anyString(), any(Object.class));
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
        then(messagingTemplate).should(never()).convertAndSend(anyString(), any(Object.class));
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

        then(messagingTemplate).should(never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("SYSTEM 메시지는 보낸 사람 정보 없이 보낸다")
    void sendSystemMessage() {
        liveChatService.sendSystemMessage(LIVE_STREAM_ID, "방송이 종료되었습니다.");

        ArgumentCaptor<ChatMessageResponse> captor = ArgumentCaptor.forClass(ChatMessageResponse.class);
        then(messagingTemplate).should().convertAndSend(eq("/topic/live-streams/10/chat"), captor.capture());
        ChatMessageResponse message = captor.getValue();
        assertThat(message.type()).isEqualTo(ChatMessageType.SYSTEM);
        assertThat(message.senderId()).isNull();
        assertThat(message.nickname()).isNull();
        assertThat(message.content()).isEqualTo("방송이 종료되었습니다.");
    }
}
