package com.example.ddangddangddang.domain.livechat.moderation;

import com.example.ddangddangddang.domain.livechat.dto.ChatMessageResponse;
import com.example.ddangddangddang.domain.livechat.dto.ModerationAlert;
import com.example.ddangddangddang.domain.livechat.moderation.ChatModerator.ChatForReview;
import com.example.ddangddangddang.domain.livechat.moderation.ChatModerator.ModerationFlag;
import com.example.ddangddangddang.domain.livechat.service.LiveChatService;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamResponse;
import com.example.ddangddangddang.domain.livestream.entity.ChatModerationMode;
import com.example.ddangddangddang.domain.livestream.service.LiveStreamService;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 전송된 채팅·선물 한마디를 방송별로 모아 두었다가, 일정 주기마다 AI로 한 번에 검토하고 처리한다.
 * 처리 방식은 방송 설정(ChatModerationMode)을 따른다.
 * - OFF: AI를 부르지 않고 버린다.
 * - ALERT: 판매자에게 알린다.
 * - AUTO_HIDE: 명백한 위반(obvious)은 시청자 화면에서 바로 가리고 판매자에게 알린다. 나머지는 ALERT처럼 알리기만 한다.
 *
 * 메시지마다 AI를 기다리면 채팅이 늦어지므로 전송은 그대로 하고, 검토는 몇 초 뒤에 따로 한다.
 * 각 서버는 자기가 받은 메시지만 검토하고, 알림·가림은 StompBroadcaster로 모든 서버의 구독자에게 전달된다.
 * AI 호출이 실패하거나 느려도 채팅 전송에는 영향이 없다. (검토 스레드가 따로 돈다)
 */
@Slf4j
@Component
public class ChatModerationQueue {

    static final String ALERT_DESTINATION = "/queue/moderation";

    private final ChatModerator moderator;
    private final LiveStreamService liveStreamService;
    private final StompBroadcaster stompBroadcaster;
    private final ChatModerationProperties.Ai settings;

    private final Map<Long, Deque<ChatMessageResponse>> pending = new ConcurrentHashMap<>();
    private ThreadPoolTaskScheduler scheduler;

    public ChatModerationQueue(ChatModerator moderator, LiveStreamService liveStreamService,
                               StompBroadcaster stompBroadcaster, ChatModerationProperties properties) {
        this.moderator = moderator;
        this.liveStreamService = liveStreamService;
        this.stompBroadcaster = stompBroadcaster;
        this.settings = properties.ai();
    }

    @PostConstruct
    void start() {
        if (!moderator.isEnabled()) {
            return;
        }
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("chat-moderation-");
        scheduler.initialize();
        scheduler.scheduleWithFixedDelay(this::flush, settings.interval());
        log.info("AI 채팅 검토 시작: model={}, effort={}, interval={}", settings.model(), settings.effort(), settings.interval());
    }

    @PreDestroy
    void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    /**
     * 채팅이나 선물 한마디를 검토 대기열에 넣는다. 내용이 없으면(한마디 없는 선물) 넣지 않는다.
     */
    public void enqueue(ChatMessageResponse message) {
        if (!moderator.isEnabled() || message.content() == null || message.content().isBlank()) {
            return;
        }
        int[] dropped = {0};
        // 큐 조회·생성과 삽입을 compute 안에서 한 번에 한다.
        // 밖에서 넣으면 flush()가 빈 큐를 맵에서 지운 직후 그 큐에 넣게 되어, 메시지가 검토되지 않고 사라질 수 있다.
        pending.compute(message.liveStreamId(), (id, queue) -> {
            Deque<ChatMessageResponse> target = queue == null ? new ConcurrentLinkedDeque<>() : queue;
            target.addLast(message);
            // 검토가 밀려 메모리가 계속 늘지 않도록, 넘치면 오래된 메시지부터 검토를 포기한다.
            while (target.size() > settings.maxPending() && target.pollFirst() != null) {
                dropped[0]++;
            }
            return target;
        });
        if (dropped[0] > 0) {
            log.warn("채팅 검토 대기열이 가득 차 오래된 메시지 {}개를 검토하지 않습니다. liveStreamId={}", dropped[0], message.liveStreamId());
        }
    }

    /**
     * 방송마다 최대 batch-size개씩 꺼내 검토한다. 스케줄러가 주기적으로 호출한다.
     */
    void flush() {
        for (Long liveStreamId : pending.keySet()) {
            Deque<ChatMessageResponse> queue = pending.get(liveStreamId);
            if (queue == null) {
                continue;
            }
            List<ChatMessageResponse> batch = drain(queue, settings.batchSize());
            if (batch.isEmpty()) {
                // 비어 있으면 정리한다. 그 사이에 새 메시지가 들어왔으면 지우지 않는다.
                pending.computeIfPresent(liveStreamId, (id, q) -> q.isEmpty() ? null : q);
                continue;
            }
            reviewAndHandle(liveStreamId, batch);
        }
    }

    private List<ChatMessageResponse> drain(Deque<ChatMessageResponse> queue, int max) {
        List<ChatMessageResponse> batch = new ArrayList<>(max);
        ChatMessageResponse message;
        while (batch.size() < max && (message = queue.pollFirst()) != null) {
            batch.add(message);
        }
        return batch;
    }

    private void reviewAndHandle(Long liveStreamId, List<ChatMessageResponse> batch) {
        // 방송 설정을 먼저 본다. 판매자가 AI 검토를 껐으면 AI를 부르지 않는다. (비용 없음)
        LiveStreamResponse liveStream;
        try {
            liveStream = liveStreamService.get(liveStreamId);
        } catch (Exception e) {
            log.warn("검토할 채팅의 방송을 찾지 못했습니다. liveStreamId={}", liveStreamId, e);
            return;
        }
        ChatModerationMode mode = liveStream.chatModerationMode();
        if (mode == ChatModerationMode.OFF) {
            return;
        }

        List<ModerationFlag> flags;
        try {
            flags = moderator.review(ReviewChannel.LIVE_STREAM, batch.stream().map(ChatModerationQueue::toReview).toList());
        } catch (Exception e) {
            // 검토는 보조 수단이다. 실패한 묶음은 다시 시도하지 않고 넘긴다. (채팅이 계속 쌓여 재시도가 밀리는 것을 막음)
            log.error("채팅 검토 실패. liveStreamId={}, count={}", liveStreamId, batch.size(), e);
            return;
        }
        if (flags.isEmpty()) {
            return;
        }

        Map<String, ChatMessageResponse> byId = batch.stream()
                .collect(Collectors.toMap(ChatMessageResponse::messageId, Function.identity(), (a, b) -> a));
        LocalDateTime now = LocalDateTime.now();
        int hidden = 0;
        for (ModerationFlag flag : flags) {
            ChatMessageResponse message = byId.get(flag.messageId());
            if (message == null) {
                continue;
            }
            boolean autoHidden = mode == ChatModerationMode.AUTO_HIDE && flag.obvious();
            if (autoHidden) {
                stompBroadcaster.broadcast(LiveChatService.chatTopic(liveStreamId),
                        ChatMessageResponse.hidden(liveStreamId, message.messageId()));
                hidden++;
            }
            stompBroadcaster.sendToUser(liveStream.sellerId(), ALERT_DESTINATION, new ModerationAlert(
                    liveStreamId, message.messageId(), message.type(), message.senderId(), message.nickname(), message.content(),
                    flag.category(), flag.category().getLabel(), flag.reason(), autoHidden, now));
        }
        log.info("채팅 검토 알림 {}건(자동 가림 {}건) 전송. liveStreamId={}, mode={}", flags.size(), hidden, liveStreamId, mode);
    }

    private static ChatForReview toReview(ChatMessageResponse message) {
        return new ChatForReview(message.messageId(), message.senderId(), message.nickname(), message.content());
    }
}
