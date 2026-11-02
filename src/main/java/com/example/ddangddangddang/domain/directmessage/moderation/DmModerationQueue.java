package com.example.ddangddangddang.domain.directmessage.moderation;

import com.example.ddangddangddang.domain.directmessage.dto.DmMessageResponse;
import com.example.ddangddangddang.domain.directmessage.entity.DmModerationFlag;
import com.example.ddangddangddang.domain.directmessage.repository.DmModerationFlagRepository;
import com.example.ddangddangddang.domain.livechat.moderation.ChatModerationProperties;
import com.example.ddangddangddang.domain.livechat.moderation.ChatModerator;
import com.example.ddangddangddang.domain.livechat.moderation.ChatModerator.ChatForReview;
import com.example.ddangddangddang.domain.livechat.moderation.ChatModerator.ModerationFlag;
import com.example.ddangddangddang.domain.livechat.moderation.ReviewChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 보낸 1:1 메시지를 모아 두었다가 일정 주기마다 AI로 한 번에 검토하고, 의심 메시지를 관리자 확인 목록(DmModerationFlag)에 남긴다.
 * 메시지를 가리거나 막지는 않는다. 1:1 대화는 당사자끼리만 보므로 판단은 관리자가 한다.
 *
 * 방송 채팅 검토(ChatModerationQueue)와 같은 AI 검토기·설정(chat.moderation.ai.*)을 쓴다. 대기열은 방송과 달리 서버 전체에 하나다.
 * AI 호출이 실패하거나 느려도 메시지 전송에는 영향이 없다. (검토 스레드가 따로 돈다)
 */
@Slf4j
@Component
public class DmModerationQueue {

    private final ChatModerator moderator;
    private final DmModerationFlagRepository flagRepository;
    private final ChatModerationProperties.Ai settings;

    private final Deque<DmMessageResponse> pending = new ConcurrentLinkedDeque<>();
    private ThreadPoolTaskScheduler scheduler;

    public DmModerationQueue(ChatModerator moderator, DmModerationFlagRepository flagRepository, ChatModerationProperties properties) {
        this.moderator = moderator;
        this.flagRepository = flagRepository;
        this.settings = properties.ai();
    }

    @PostConstruct
    void start() {
        if (!moderator.isEnabled()) {
            return;
        }
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("dm-moderation-");
        scheduler.initialize();
        scheduler.scheduleWithFixedDelay(this::flush, settings.interval());
        log.info("AI 1:1 메시지 검토 시작: model={}, interval={}", settings.model(), settings.interval());
    }

    @PreDestroy
    void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    public void enqueue(DmMessageResponse message) {
        if (!moderator.isEnabled()) {
            return;
        }
        pending.addLast(message);
        // 검토가 밀려 메모리가 계속 늘지 않도록, 넘치면 오래된 메시지부터 검토를 포기한다.
        int dropped = 0;
        while (pending.size() > settings.maxPending() && pending.pollFirst() != null) {
            dropped++;
        }
        if (dropped > 0) {
            log.warn("1:1 메시지 검토 대기열이 가득 차 오래된 메시지 {}개를 검토하지 않습니다.", dropped);
        }
    }

    /**
     * 최대 batch-size개씩 꺼내 검토한다. 스케줄러가 주기적으로 호출한다.
     */
    void flush() {
        List<DmMessageResponse> batch = new ArrayList<>(settings.batchSize());
        DmMessageResponse message;
        while (batch.size() < settings.batchSize() && (message = pending.pollFirst()) != null) {
            batch.add(message);
        }
        if (batch.isEmpty()) {
            return;
        }

        List<ModerationFlag> flags;
        try {
            flags = moderator.review(ReviewChannel.DIRECT_MESSAGE, batch.stream().map(DmModerationQueue::toReview).toList());
        } catch (Exception e) {
            // 검토는 보조 수단이다. 실패한 묶음은 다시 시도하지 않고 넘긴다.
            log.error("1:1 메시지 검토 실패. count={}", batch.size(), e);
            return;
        }

        Map<String, DmMessageResponse> byId = batch.stream()
                .collect(Collectors.toMap(m -> String.valueOf(m.messageId()), Function.identity(), (a, b) -> a));
        int saved = 0;
        for (ModerationFlag flag : flags) {
            DmMessageResponse flagged = byId.get(flag.messageId());
            if (flagged == null || flagRepository.existsByMessageId(flagged.messageId())) {
                continue;
            }
            try {
                flagRepository.save(DmModerationFlag.create(flagged.roomId(), flagged.messageId(), flagged.senderId(),
                        flag.category(), flag.reason()));
                saved++;
            } catch (DataIntegrityViolationException e) {
                // 다른 서버가 같은 메시지를 먼저 표시한 경우 (거의 없음)
                log.debug("이미 표시된 1:1 메시지입니다. messageId={}", flagged.messageId());
            }
        }
        if (saved > 0) {
            log.info("1:1 메시지 검토: 관리자 확인 필요 {}건", saved);
        }
    }

    private static ChatForReview toReview(DmMessageResponse message) {
        return new ChatForReview(String.valueOf(message.messageId()), message.senderId(), null, message.content());
    }
}
