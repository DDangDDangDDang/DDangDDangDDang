package com.example.ddangddangddang.domain.livechat.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * /topic/live-streams/{liveStreamId}/chat 구독자에게 전송되는 메시지.
 * SYSTEM 메시지는 senderId, nickname이 null이다.
 * DONATION 메시지는 선물한 사람(senderId·nickname)과 선물한 포인트(amount), 함께 보낸 한마디(content, 없으면 null)를 담는다.
 * BID 메시지는 입찰한 사람(senderId·nickname), 입찰 금액(amount), 직전 현재가(previousAmount, 첫 입찰이면 시작가), 경매(auctionId·productName)를 담는다.
 * AUCTION_SOLD 메시지는 낙찰받은 사람(senderId·nickname), 낙찰가(amount), 경매(auctionId·productName)를 담는다.
 * BID·AUCTION_SOLD의 content는 채팅에 그대로 보여 줄 안내 문장이다.
 * amount는 DONATION·BID·AUCTION_SOLD, previousAmount는 BID, auctionId·productName은 BID·AUCTION_SOLD에만 있다. (나머지는 null)
 * messageId는 메시지를 가리킬 때 쓴다. (판매자 검토 알림 → 해당 메시지 찾기)
 */
public record ChatMessageResponse(
        String messageId,
        ChatMessageType type,
        Long liveStreamId,
        Long senderId,
        String nickname,
        String content,
        LocalDateTime sentAt,
        Long amount,
        Long previousAmount,
        Long auctionId,
        String productName
) {
    public static ChatMessageResponse chat(Long liveStreamId, Long senderId, String nickname, String content) {
        return new ChatMessageResponse(newId(), ChatMessageType.CHAT, liveStreamId, senderId, nickname, content, LocalDateTime.now(), null, null, null, null);
    }

    public static ChatMessageResponse system(Long liveStreamId, String content) {
        return new ChatMessageResponse(newId(), ChatMessageType.SYSTEM, liveStreamId, null, null, content, LocalDateTime.now(), null, null, null, null);
    }

    public static ChatMessageResponse donation(Long liveStreamId, Long senderId, String nickname, long amount, String message) {
        return new ChatMessageResponse(newId(), ChatMessageType.DONATION, liveStreamId, senderId, nickname, message, LocalDateTime.now(), amount, null, null, null);
    }

    public static ChatMessageResponse bid(Long liveStreamId, Long bidderId, String nickname, Long auctionId, String productName,
                                          long amount, long previousAmount) {
        String content = "%s님이 ‘%s’ 상품을 %,dP에 입찰했습니다.".formatted(nickname, productName, amount);
        return new ChatMessageResponse(newId(), ChatMessageType.BID, liveStreamId, bidderId, nickname, content, LocalDateTime.now(),
                amount, previousAmount, auctionId, productName);
    }

    public static ChatMessageResponse auctionSold(Long liveStreamId, Long winnerId, String nickname, Long auctionId, String productName, long price) {
        String content = "‘%s’ 상품이 %s님에게 %,dP에 낙찰되었습니다.".formatted(productName, nickname, price);
        return new ChatMessageResponse(newId(), ChatMessageType.AUCTION_SOLD, liveStreamId, winnerId, nickname, content, LocalDateTime.now(),
                price, null, auctionId, productName);
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
