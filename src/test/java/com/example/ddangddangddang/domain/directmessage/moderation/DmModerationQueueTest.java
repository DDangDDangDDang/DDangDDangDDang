package com.example.ddangddangddang.domain.directmessage.moderation;

import com.example.ddangddangddang.domain.directmessage.dto.DmMessageResponse;
import com.example.ddangddangddang.domain.directmessage.entity.DmModerationFlag;
import com.example.ddangddangddang.domain.directmessage.repository.DmModerationFlagRepository;
import com.example.ddangddangddang.domain.livechat.moderation.ChatModerationProperties;
import com.example.ddangddangddang.domain.livechat.moderation.ChatModerator;
import com.example.ddangddangddang.domain.livechat.moderation.ModerationCategory;
import com.example.ddangddangddang.domain.livechat.moderation.ReviewChannel;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class DmModerationQueueTest {

    private static final Long ROOM_ID = 5L;

    @Mock
    private DmModerationFlagRepository flagRepository;

    @Test
    @DisplayName("AI가 표시한 1:1 메시지를 관리자 확인 목록에 남기고, 검토 요청은 1:1 메시지로 보낸다")
    void saveFlag() {
        FakeModerator moderator = new FakeModerator(true, "직거래");
        DmModerationQueue queue = queue(moderator, 50, 500);

        queue.enqueue(message(1L, 10L, "사이즈 있나요?"));
        queue.enqueue(message(2L, 11L, "직거래로 하면 깎아드릴게요"));
        queue.flush();

        ArgumentCaptor<DmModerationFlag> captor = ArgumentCaptor.forClass(DmModerationFlag.class);
        then(flagRepository).should().save(captor.capture());
        DmModerationFlag flag = captor.getValue();
        assertThat(flag.getMessageId()).isEqualTo(2L);
        assertThat(flag.getRoomId()).isEqualTo(ROOM_ID);
        assertThat(flag.getSenderId()).isEqualTo(11L);
        assertThat(flag.getCategory()).isEqualTo(ModerationCategory.EXTERNAL_TRADE);
        assertThat(flag.getReason()).isEqualTo("직거래를 제안합니다.");
        assertThat(moderator.channels).containsExactly(ReviewChannel.DIRECT_MESSAGE);
        assertThat(moderator.batches.get(0)).extracting(ChatModerator.ChatForReview::messageId).containsExactly("1", "2");
    }

    @Test
    @DisplayName("AI가 쓴 이유가 길면 컬럼 길이(500자)에 맞게 잘라 저장한다")
    void truncateLongReason() {
        DmModerationFlag flag = DmModerationFlag.create(ROOM_ID, 1L, 10L, ModerationCategory.SPAM, "가".repeat(600));

        assertThat(flag.getReason()).hasSize(500);
    }

    @Test
    @DisplayName("이미 표시된 메시지는 다시 저장하지 않는다")
    void skipAlreadyFlagged() {
        DmModerationQueue queue = queue(new FakeModerator(true, "직거래"), 50, 500);
        given(flagRepository.existsByMessageId(2L)).willReturn(true);

        queue.enqueue(message(2L, 11L, "직거래 가능"));
        queue.flush();

        then(flagRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("AI 호출이 실패해도 예외를 던지지 않고, 다음 묶음은 계속 검토한다")
    void reviewFailure() {
        FakeModerator moderator = new FakeModerator(true, "직거래");
        moderator.failNext = true;
        DmModerationQueue queue = queue(moderator, 50, 500);

        queue.enqueue(message(1L, 10L, "직거래 가능"));
        queue.flush();
        queue.enqueue(message(2L, 10L, "직거래 가능"));
        queue.flush();

        assertThat(moderator.batches).hasSize(2);
        then(flagRepository).should().save(any());
    }

    @Test
    @DisplayName("batch-size개씩 검토하고, max-pending을 넘으면 오래된 것부터 버린다")
    void batchAndMaxPending() {
        FakeModerator moderator = new FakeModerator(true, null);
        DmModerationQueue queue = queue(moderator, 2, 3);
        for (long i = 1; i <= 5; i++) {
            queue.enqueue(message(i, 10L, "메시지 " + i));
        }

        queue.flush();
        queue.flush();
        queue.flush();

        assertThat(moderator.batches).extracting(List::size).containsExactly(2, 1);
        assertThat(moderator.batches.get(0)).extracting(ChatModerator.ChatForReview::content).containsExactly("메시지 3", "메시지 4");
    }

    @Test
    @DisplayName("AI 검토를 끈 상태면 메시지를 모으지 않는다")
    void disabled() {
        FakeModerator moderator = new FakeModerator(false, "직거래");
        DmModerationQueue queue = queue(moderator, 50, 500);

        queue.enqueue(message(1L, 10L, "직거래 가능"));
        queue.flush();

        assertThat(moderator.batches).isEmpty();
    }

    private DmModerationQueue queue(ChatModerator moderator, int batchSize, int maxPending) {
        ChatModerationProperties properties = new ChatModerationProperties(List.of(),
                new ChatModerationProperties.Ai(moderator.isEnabled(), null, null, Duration.ofSeconds(3), batchSize, maxPending, null));
        // start()를 부르지 않아 스케줄러는 돌지 않는다. flush()를 직접 호출해 확인한다.
        return new DmModerationQueue(moderator, flagRepository, properties);
    }

    private DmMessageResponse message(Long messageId, Long senderId, String content) {
        return new DmMessageResponse(messageId, ROOM_ID, senderId, "client-" + messageId, content, LocalDateTime.now());
    }

    /** 받은 묶음을 기록하고, 정해 둔 문구가 들어간 메시지를 외부 거래 유도로 표시하는 가짜 검토기 */
    private static class FakeModerator implements ChatModerator {

        private final boolean enabled;
        private final String keyword;
        private final List<List<ChatForReview>> batches = new ArrayList<>();
        private final List<ReviewChannel> channels = new ArrayList<>();
        private boolean failNext;

        FakeModerator(boolean enabled, String keyword) {
            this.enabled = enabled;
            this.keyword = keyword;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public List<ModerationFlag> review(ReviewChannel channel, List<ChatForReview> messages) {
            channels.add(channel);
            batches.add(List.copyOf(messages));
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("AI 호출 실패");
            }
            if (keyword == null) {
                return List.of();
            }
            return messages.stream()
                    .filter(m -> m.content().contains(keyword))
                    .map(m -> new ModerationFlag(m.messageId(), ModerationCategory.EXTERNAL_TRADE, "직거래를 제안합니다.", false))
                    .toList();
        }
    }
}
