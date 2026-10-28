package com.example.ddangddangddang.domain.livechat.moderation;

import com.example.ddangddangddang.domain.livechat.dto.ChatMessageResponse;
import com.example.ddangddangddang.domain.livechat.dto.ModerationAlert;
import com.example.ddangddangddang.domain.livechat.moderation.ChatModerator.ChatForReview;
import com.example.ddangddangddang.domain.livechat.moderation.ChatModerator.ModerationFlag;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamResponse;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.service.LiveStreamService;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ChatModerationQueueTest {

    private static final Long LIVE_STREAM_ID = 10L;
    private static final Long SELLER_ID = 99L;

    @Mock
    private LiveStreamService liveStreamService;

    @Mock
    private StompBroadcaster stompBroadcaster;

    @Test
    @DisplayName("AI가 표시한 메시지만 판매자에게 알리고, 알림에는 보낸 사람과 이유가 담긴다")
    void notifySeller() {
        RecordingModerator moderator = new RecordingModerator(true);
        ChatModerationQueue queue = queue(moderator, 50, 500);
        ChatMessageResponse ok = chat(1L, "회원1", "사이즈 몇이에요?");
        ChatMessageResponse bad = chat(2L, "회원2", "카톡 아디 seller77 로 따로 연락주세요");
        moderator.flagContents("카톡 아디", ModerationCategory.EXTERNAL_TRADE, "외부 메신저로 직거래를 유도합니다.");
        given(liveStreamService.get(LIVE_STREAM_ID)).willReturn(liveStream());

        queue.enqueue(ok);
        queue.enqueue(bad);
        queue.flush();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        then(stompBroadcaster).should().sendToUser(eq(SELLER_ID), eq("/queue/moderation"), captor.capture());
        ModerationAlert alert = (ModerationAlert) captor.getValue();
        assertThat(alert.messageId()).isEqualTo(bad.messageId());
        assertThat(alert.senderId()).isEqualTo(2L);
        assertThat(alert.nickname()).isEqualTo("회원2");
        assertThat(alert.content()).isEqualTo(bad.content());
        assertThat(alert.category()).isEqualTo(ModerationCategory.EXTERNAL_TRADE);
        assertThat(alert.categoryLabel()).isEqualTo("외부 거래 유도");
        assertThat(alert.reason()).isEqualTo("외부 메신저로 직거래를 유도합니다.");
        assertThat(moderator.batches).hasSize(1);
        assertThat(moderator.batches.get(0)).hasSize(2);
    }

    @Test
    @DisplayName("표시된 메시지가 없으면 알림도, 방송 조회도 하지 않는다")
    void nothingFlagged() {
        ChatModerationQueue queue = queue(new RecordingModerator(true), 50, 500);

        queue.enqueue(chat(1L, "회원1", "안녕하세요"));
        queue.flush();

        then(liveStreamService).should(never()).get(any());
        then(stompBroadcaster).should(never()).sendToUser(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("한 번에 batch-size개까지만 검토하고, 남은 메시지는 다음 주기에 검토한다")
    void batchSize() {
        RecordingModerator moderator = new RecordingModerator(true);
        ChatModerationQueue queue = queue(moderator, 2, 500);
        for (int i = 0; i < 5; i++) {
            queue.enqueue(chat(1L, "회원1", "메시지 " + i));
        }

        queue.flush();
        queue.flush();
        queue.flush();

        assertThat(moderator.batches).extracting(List::size).containsExactly(2, 2, 1);
    }

    @Test
    @DisplayName("검토 대기 메시지가 max-pending을 넘으면 오래된 것부터 버린다")
    void maxPending() {
        RecordingModerator moderator = new RecordingModerator(true);
        ChatModerationQueue queue = queue(moderator, 50, 3);
        for (int i = 0; i < 5; i++) {
            queue.enqueue(chat(1L, "회원1", "메시지 " + i));
        }

        queue.flush();

        assertThat(moderator.batches.get(0)).extracting(ChatForReview::content)
                .containsExactly("메시지 2", "메시지 3", "메시지 4");
    }

    @Test
    @DisplayName("빈 큐가 정리된 뒤에 들어온 메시지도 빠짐없이 검토한다")
    void enqueueAfterEmptyQueueRemoved() {
        RecordingModerator moderator = new RecordingModerator(true);
        ChatModerationQueue queue = queue(moderator, 50, 500);

        queue.enqueue(chat(1L, "회원1", "첫 메시지"));
        queue.flush();
        queue.flush(); // 빈 큐를 맵에서 지운다.
        queue.enqueue(chat(1L, "회원1", "두 번째 메시지"));
        queue.flush();

        assertThat(moderator.batches).extracting(batch -> batch.get(0).content())
                .containsExactly("첫 메시지", "두 번째 메시지");
    }

    @Test
    @DisplayName("AI 호출이 실패해도 예외를 밖으로 던지지 않고, 다음 메시지는 계속 검토한다")
    void reviewFailure() {
        RecordingModerator moderator = new RecordingModerator(true);
        moderator.failNext = true;
        ChatModerationQueue queue = queue(moderator, 50, 500);

        queue.enqueue(chat(1L, "회원1", "첫 메시지"));
        queue.flush();
        queue.enqueue(chat(1L, "회원1", "두 번째 메시지"));
        queue.flush();

        assertThat(moderator.batches).hasSize(2);
        then(stompBroadcaster).should(never()).sendToUser(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("AI 검토를 끈 상태면 메시지를 모으지 않는다")
    void disabled() {
        RecordingModerator moderator = new RecordingModerator(false);
        ChatModerationQueue queue = queue(moderator, 50, 500);

        queue.enqueue(chat(1L, "회원1", "안녕하세요"));
        queue.flush();

        assertThat(moderator.batches).isEmpty();
    }

    private ChatModerationQueue queue(ChatModerator moderator, int batchSize, int maxPending) {
        ChatModerationProperties properties = new ChatModerationProperties(List.of(),
                new ChatModerationProperties.Ai(moderator.isEnabled(), null, null, Duration.ofSeconds(3), batchSize, maxPending));
        // start()를 부르지 않아 스케줄러는 돌지 않는다. flush()를 직접 호출해 확인한다.
        return new ChatModerationQueue(moderator, liveStreamService, stompBroadcaster, properties);
    }

    private ChatMessageResponse chat(Long senderId, String nickname, String content) {
        return ChatMessageResponse.chat(LIVE_STREAM_ID, senderId, nickname, null, content);
    }

    private LiveStreamResponse liveStream() {
        return new LiveStreamResponse(LIVE_STREAM_ID, SELLER_ID, "테스트 방송", null, List.of(), null, List.of(), null, false, LiveStreamStatus.LIVE,
                LocalDateTime.now(), null, LocalDateTime.now(), null);
    }

    /** 받은 묶음을 기록하고, 정해 둔 문구가 들어간 메시지를 표시하는 가짜 검토기 */
    private static class RecordingModerator implements ChatModerator {

        private final boolean enabled;
        private final List<List<ChatForReview>> batches = new ArrayList<>();
        private String flagKeyword;
        private ModerationCategory flagCategory;
        private String flagReason;
        private boolean failNext;

        RecordingModerator(boolean enabled) {
            this.enabled = enabled;
        }

        void flagContents(String keyword, ModerationCategory category, String reason) {
            this.flagKeyword = keyword;
            this.flagCategory = category;
            this.flagReason = reason;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public List<ModerationFlag> review(List<ChatForReview> messages) {
            batches.add(List.copyOf(messages));
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("AI 호출 실패");
            }
            if (flagKeyword == null) {
                return List.of();
            }
            return messages.stream()
                    .filter(m -> m.content().contains(flagKeyword))
                    .map(m -> new ModerationFlag(m.messageId(), flagCategory, flagReason))
                    .toList();
        }
    }
}
