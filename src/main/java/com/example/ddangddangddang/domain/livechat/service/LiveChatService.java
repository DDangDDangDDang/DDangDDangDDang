package com.example.ddangddangddang.domain.livechat.service;

import com.example.ddangddangddang.domain.livechat.dto.ChatMessageResponse;
import com.example.ddangddangddang.domain.livechat.exception.LiveChatErrorCode;
import com.example.ddangddangddang.domain.livechat.repository.ChatRateLimitRepository;
import com.example.ddangddangddang.domain.livestream.service.LiveStreamService;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.security.AuthUser;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class LiveChatService {

    private static final String CHAT_TOPIC = "/topic/live-streams/%d/chat";

    // 도배 방지: 회원당 5초에 5개까지, 넘으면 그 순간부터 30초 채팅 금지
    static final int RATE_LIMIT_MAX_MESSAGES = 5;
    static final Duration RATE_LIMIT_WINDOW = Duration.ofSeconds(5);
    static final Duration MUTE_DURATION = Duration.ofSeconds(30);

    private final StompBroadcaster stompBroadcaster;
    private final ChatRateLimitRepository chatRateLimitRepository;
    private final LiveStreamService liveStreamService;

    // TODO: 금칙어 (목록 관리 방식 확정 후)
    public void send(Long liveStreamId, AuthUser sender, String content) {
        // 도배 메시지가 DB 조회까지 가지 않도록 Redis 확인을 먼저 한다.
        validateRateLimit(sender.id());
        liveStreamService.validateLive(liveStreamId);

        broadcast(liveStreamId, ChatMessageResponse.chat(liveStreamId, sender.id(), sender.nickname(), content.strip()));
    }

    public void sendSystemMessage(Long liveStreamId, String content) {
        broadcast(liveStreamId, ChatMessageResponse.system(liveStreamId, content));
    }

    /**
     * 금지 중이면 횟수를 세지 않고 바로 거부한다. 금지 중에 계속 보내도 금지 시간은 늘어나지 않는다.
     */
    private void validateRateLimit(Long userId) {
        Duration muteRemaining = chatRateLimitRepository.getMuteRemaining(userId);
        if (!muteRemaining.isZero()) {
            throw new BusinessException(LiveChatErrorCode.CHAT_MUTED, mutedMessage(muteRemaining));
        }
        if (chatRateLimitRepository.increment(userId, RATE_LIMIT_WINDOW) > RATE_LIMIT_MAX_MESSAGES) {
            chatRateLimitRepository.mute(userId, MUTE_DURATION);
            throw new BusinessException(LiveChatErrorCode.TOO_MANY_MESSAGES);
        }
    }

    // 1초 미만이 남았을 때 "0초 후"로 보이지 않도록 초 단위로 올림한다.
    static String mutedMessage(Duration remaining) {
        long seconds = (remaining.toMillis() + 999) / 1000;
        return "%s %d초 후에 다시 보낼 수 있습니다.".formatted(LiveChatErrorCode.CHAT_MUTED.getMessage(), seconds);
    }

    private void broadcast(Long liveStreamId, ChatMessageResponse message) {
        stompBroadcaster.broadcast(CHAT_TOPIC.formatted(liveStreamId), message);
    }
}
