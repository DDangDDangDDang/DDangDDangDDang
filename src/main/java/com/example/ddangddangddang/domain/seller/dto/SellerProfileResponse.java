package com.example.ddangddangddang.domain.seller.dto;

import java.time.LocalDateTime;

/**
 * 판매자 프로필 (프로필 창 기본 탭). 로그인하지 않아도 볼 수 있다.
 *
 * - verifiedSeller: 관리자가 판매자 신청을 승인한 회원. storeName·introduction·verifiedAt은 인증 회원만 있다. (아니면 null)
 * - subscribed: 내가 구독 중인지. 로그인하지 않았으면 false
 * - liveStreamId: 지금 방송 중이면 그 방송 (아니면 null)
 * - 방송: firstBroadcastAt(첫 방송 시작, 방송한 적 없으면 null), broadcastCount(시작한 방송 수), totalBroadcastSeconds(방송한 시간 합, 방송 중이면 지금까지)
 * - 신뢰 지표(trust): 판매자를 믿을 수 있는지 판단할 자료
 */
public record SellerProfileResponse(
        Long sellerId,
        String nickname,
        boolean verifiedSeller,
        String storeName,
        String introduction,
        LocalDateTime verifiedAt,
        LocalDateTime joinedAt,
        long subscriberCount,
        boolean subscribed,
        Long liveStreamId,
        LocalDateTime firstBroadcastAt,
        long broadcastCount,
        long totalBroadcastSeconds,
        Trust trust
) {
    /**
     * - phoneVerified: 가입할 때 휴대폰 본인 인증을 했는지
     * - soldAuctionCount: 낙찰된 경매 수, failedAuctionCount: 입찰 없이 끝난(유찰) 경매 수, canceledAuctionCount: 판매자가 취소한 경매 수
     * - completedOrderCount: 구매자가 구매 확정한 거래 수 (정상 거래 완료)
     * - averageShippingHours: 낙찰(주문) 뒤 발송까지 걸린 평균 시간. 최근 발송한 주문 기준, 발송한 적 없으면 null
     */
    public record Trust(
            boolean phoneVerified,
            long soldAuctionCount,
            long failedAuctionCount,
            long canceledAuctionCount,
            long completedOrderCount,
            Double averageShippingHours
    ) {
    }
}
