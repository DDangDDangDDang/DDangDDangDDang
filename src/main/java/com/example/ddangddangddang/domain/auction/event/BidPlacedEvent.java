package com.example.ddangddangddang.domain.auction.event;

import java.time.LocalDateTime;

/**
 * 입찰이 커밋된 뒤 경매를 보고 있는 사람들의 화면(현재가, 최고 입찰자, 남은 시간)을 갱신하기 위한 이벤트.
 */
public record BidPlacedEvent(
        Long auctionId,
        Long bidderId,
        long amount,
        int bidCount,
        LocalDateTime endAt,
        boolean extended,
        // 입찰자 닉네임 (로그인 토큰에서 받음). 없으면 실시간 전송 때 DB에서 찾는다 (AuctionEventListener)
        String bidderNickname
) {
}
