package com.example.ddangddangddang.domain.auction.event;

import com.example.ddangddangddang.domain.auction.dto.response.AuctionRealtimeMessage;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.util.NicknameMasker;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 입찰·종료를 경매 구독자에게 알린다. 커밋된 뒤에만 보낸다. 롤백되면 알림도 나가지 않는다.
 * 프론트는 /topic/auctions/{auctionId} 를 구독한다.
 *
 * 알림 실패(Redis 장애 등)는 로그만 남긴다. 이미 커밋된 입찰을 사용자에게 실패처럼 보이게 하지 않기 위해서다.
 *
 * 손님도 구독하므로 이벤트를 그대로 보내지 않고, 회원 번호를 뺀 메시지로 바꿔 보낸다. (입찰자는 가린 닉네임)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionEventListener {

    static final String AUCTION_TOPIC = "/topic/auctions/%d";

    private final StompBroadcaster stompBroadcaster;
    private final UserRepository userRepository;

    @TransactionalEventListener
    public void handleBidPlaced(BidPlacedEvent event) {
        broadcast(event.auctionId(), AuctionRealtimeMessage.BidPlaced.of(event, NicknameMasker.mask(nickname(event.bidderId()))));
    }

    @TransactionalEventListener
    public void handleAuctionEnded(AuctionEndedEvent event) {
        broadcast(event.auctionId(), AuctionRealtimeMessage.Ended.of(event));
    }

    // 닉네임을 못 불러와도(회원 없음, DB 오류) 현재가 갱신은 보내야 하므로 "알 수 없음"으로 보낸다
    private String nickname(Long userId) {
        try {
            return userRepository.findById(userId).map(User::getNickname).orElse(null);
        } catch (Exception e) {
            log.warn("입찰자 닉네임 조회 실패 userId={}", userId, e);
            return null;
        }
    }

    private void broadcast(Long auctionId, Object payload) {
        try {
            stompBroadcaster.broadcast(AUCTION_TOPIC.formatted(auctionId), payload);
        } catch (Exception e) {
            log.warn("경매 알림 전송 실패 auctionId={}", auctionId, e);
        }
    }
}
