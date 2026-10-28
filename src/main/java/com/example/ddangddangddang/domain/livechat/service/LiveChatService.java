package com.example.ddangddangddang.domain.livechat.service;

import com.example.ddangddangddang.domain.user.entity.NicknameBadge;
import com.example.ddangddangddang.domain.user.service.NicknameBadges;

import com.example.ddangddangddang.domain.livechat.dto.ChatMessageResponse;
import com.example.ddangddangddang.domain.livechat.exception.LiveChatErrorCode;
import com.example.ddangddangddang.domain.livechat.moderation.ChatContentFilter;
import com.example.ddangddangddang.domain.livechat.moderation.ChatModerationQueue;
import com.example.ddangddangddang.domain.livechat.repository.ChatRateLimitRepository;
import com.example.ddangddangddang.domain.livestream.service.LiveStreamService;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.exception.CommonErrorCode;
import com.example.ddangddangddang.global.security.AuthUser;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LiveChatService {

    private static final String CHAT_TOPIC = "/topic/live-streams/%d/chat";

    // 도배 방지: 회원당 5초에 5개까지, 넘으면 그 순간부터 30초 채팅 금지
    static final int RATE_LIMIT_MAX_MESSAGES = 5;
    static final Duration RATE_LIMIT_WINDOW = Duration.ofSeconds(5);
    static final Duration MUTE_DURATION = Duration.ofSeconds(30);

    // 같은 문장 반복 방지: 직전 메시지와 같은 내용은 연달아 2번까지. 마지막으로 보낸 지 30초가 지나면 다시 센다.
    static final int MAX_REPEATED_MESSAGES = 2;
    static final Duration REPEAT_WINDOW = Duration.ofSeconds(30);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final StompBroadcaster stompBroadcaster;
    private final ChatRateLimitRepository chatRateLimitRepository;
    private final LiveStreamService liveStreamService;
    private final ChatContentFilter chatContentFilter;
    private final ChatModerationQueue chatModerationQueue;
    private final NicknameBadges nicknameBadges;

    public static String chatTopic(Long liveStreamId) {
        return CHAT_TOPIC.formatted(liveStreamId);
    }

    public void send(Long liveStreamId, AuthUser sender, String content) {
        String trimmed = content.strip();
        // 도배 메시지가 DB 조회까지 가지 않도록 Redis 확인을 먼저 한다.
        validateRateLimit(sender.id());
        // 연락처·외부 링크·메신저 아이디·계좌번호·금칙어는 보내기 전에 막는다.
        chatContentFilter.validate(trimmed);
        validateNotRepeated(sender.id(), trimmed);
        liveStreamService.validateLive(liveStreamId);

        // 보낸 사람이 장착한 닉네임 뱃지를 함께 보낸다. (메시지마다 읽으므로 캐시, NicknameBadges)
        NicknameBadge badge = nicknameBadges.equipped(sender.id()).badge();
        ChatMessageResponse message = ChatMessageResponse.chat(liveStreamId, sender.id(), sender.nickname(), badge, trimmed);
        broadcast(liveStreamId, message);
        // 애매한 내용은 전송한 뒤 AI가 따로 검토해 방송 설정대로 처리한다. (AI를 끈 상태면 아무것도 하지 않음)
        chatModerationQueue.enqueue(message);
    }

    public void sendSystemMessage(Long liveStreamId, String content) {
        broadcast(liveStreamId, ChatMessageResponse.system(liveStreamId, content));
    }

    /**
     * 도네이션 선물 안내. 방송 화면은 이 메시지로 선물 이펙트를 띄우고 채팅에도 남긴다.
     * 한마디는 보내기 전에 규칙 검사를 거쳤고(DonationService), 여기서 채팅처럼 AI 검토 대기열에도 넣는다.
     */
    public void sendDonationMessage(Long liveStreamId, Long senderId, String nickname, long amount, String message) {
        ChatMessageResponse donation = ChatMessageResponse.donation(liveStreamId, senderId, nickname, amount, message);
        broadcast(liveStreamId, donation);
        chatModerationQueue.enqueue(donation);
    }

    /** 방송에 올라간 경매의 입찰 안내. 방송 화면은 이 메시지로 입찰 이펙트를 띄우고 채팅에도 남긴다. */
    public void sendBidMessage(Long liveStreamId, Long bidderId, String nickname, Long auctionId, String productName, long amount, long previousAmount) {
        broadcast(liveStreamId, ChatMessageResponse.bid(liveStreamId, bidderId, nickname, auctionId, productName, amount, previousAmount));
    }

    /** 방송에 올라간 경매의 낙찰 안내. 방송 화면은 이 메시지로 낙찰 이펙트를 띄우고 채팅에도 남긴다. */
    public void sendAuctionSoldMessage(Long liveStreamId, Long winnerId, String nickname, Long auctionId, String productName, long price) {
        broadcast(liveStreamId, ChatMessageResponse.auctionSold(liveStreamId, winnerId, nickname, auctionId, productName, price));
    }

    /**
     * 판매자가 AI가 가린 메시지를 되돌린다. 시청자 화면은 가렸던 메시지의 원래 내용을 다시 보여 준다.
     * 채팅은 서버에 저장하지 않으므로 가린 기록을 확인하지 않는다. 가리지 않은 메시지를 되돌려도 화면에는 변화가 없다.
     */
    public void restoreHiddenMessage(Long liveStreamId, Long sellerId, String messageId) {
        validateMessageId(messageId);
        if (!liveStreamService.get(liveStreamId).sellerId().equals(sellerId)) {
            throw new BusinessException(LiveChatErrorCode.NOT_STREAM_OWNER);
        }
        broadcast(liveStreamId, ChatMessageResponse.restored(liveStreamId, messageId));
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

    // 띄어쓰기와 대소문자만 다른 문장("ㅋㅋ ㅋㅋ", "ㅋㅋㅋㅋ")은 같은 내용으로 본다.
    private void validateNotRepeated(Long userId, String content) {
        String key = WHITESPACE.matcher(content.toLowerCase(Locale.ROOT)).replaceAll("");
        if (chatRateLimitRepository.countRepeated(userId, key, REPEAT_WINDOW) > MAX_REPEATED_MESSAGES) {
            throw new BusinessException(LiveChatErrorCode.REPEATED_MESSAGE);
        }
    }

    // 채팅 messageId는 서버가 만든 UUID다. 다른 값은 시청자 화면에 보낼 이유가 없다.
    private static void validateMessageId(String messageId) {
        try {
            UUID.fromString(messageId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "메시지 ID 형식이 올바르지 않습니다.");
        }
    }

    // 1초 미만이 남았을 때 "0초 후"로 보이지 않도록 초 단위로 올림한다.
    static String mutedMessage(Duration remaining) {
        long seconds = (remaining.toMillis() + 999) / 1000;
        return "%s %d초 후에 다시 보낼 수 있습니다.".formatted(LiveChatErrorCode.CHAT_MUTED.getMessage(), seconds);
    }

    private void broadcast(Long liveStreamId, ChatMessageResponse message) {
        stompBroadcaster.broadcast(chatTopic(liveStreamId), message);
    }
}
