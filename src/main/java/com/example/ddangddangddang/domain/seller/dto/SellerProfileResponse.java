package com.example.ddangddangddang.domain.seller.dto;

import java.time.LocalDateTime;

/**
 * 판매자 프로필 (프로필 창 기본 탭). 로그인하지 않아도 볼 수 있다.
 *
 * - bio: 회원 자기소개, profileImageUrl: 프로필 이미지 주소 (없으면 null)
 * - businessSeller: 사업자등록번호로 승인받은 사업자 판매자 (판매자 인증 배지 옆에 사업자 배지)
 * - verifiedSeller: 관리자가 판매자 신청을 승인한 회원. storeName·introduction·verifiedAt은 인증 회원만 있다. (아니면 null)
 * - subscribed: 내가 구독 중인지. 로그인하지 않았으면 false
 * - liveStreamId: 지금 방송 중이면 그 방송 (아니면 null)
 * - 방송: firstBroadcastAt(첫 방송 시작, 방송한 적 없으면 null), broadcastCount(시작한 방송 수), totalBroadcastSeconds(방송한 시간 합, 방송 중이면 지금까지)
 * - powerSeller: 판매자 상점에서 파워 판매자를 사서 지금 기간 중인지 (프로필에 파워 판매자 배지)
 * - 신뢰 지표(trust): 판매자를 믿을 수 있는지 판단할 자료
 */
public record SellerProfileResponse(
        Long sellerId,
        String nickname,
        String bio,
        String profileImageUrl,
        boolean verifiedSeller,
        boolean businessSeller,
        boolean powerSeller,
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
     * - soldAuctionCount: 낙찰된 경매 수, failedAuctionCount: 입찰 없이 끝난(유찰) 경매 수, canceledAuctionCount: 판매자가 취소한 경매 수
     * - completedOrderCount: 구매자가 구매 확정한 거래 수 (정상 거래 완료)
     * - averageShippingHours: 낙찰(주문) 뒤 발송까지 걸린 평균 시간. 최근 발송한 주문 기준, 발송한 적 없으면 null
     * - 응원(도네이션): 최근 donationWindowDays일 동안 방송에서 도네이션 포인트를 선물한 서로 다른 사람 수(donorCount)와 받은 포인트(receivedDonationPoints).
     *   도네이션 포인트는 구매 확정한 회원만 받으므로, 실제로 거래한 사람들이 응원했다는 뜻이다.
     */
    public record Trust(
            long soldAuctionCount,
            long failedAuctionCount,
            long canceledAuctionCount,
            long completedOrderCount,
            Double averageShippingHours,
            long donorCount,
            long receivedDonationPoints,
            long donationWindowDays
    ) {
    }
}
