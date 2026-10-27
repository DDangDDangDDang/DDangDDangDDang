package com.example.ddangddangddang.domain.seller.dto;

/**
 * 내가 구독한 판매자 (홈 화면 왼쪽 목록).
 * liveStreamId·liveTitle: 지금 방송 중이면 그 방송, 아니면 null
 * liveRecorded: 지금 방송이 미리 녹화한 영상이면 true (방송 중이 아니면 false)
 * nickname: 탈퇴 등으로 회원을 찾지 못하면 null
 */
public record SubscribedSellerResponse(
        Long sellerId,
        String nickname,
        boolean verifiedSeller,
        Long liveStreamId,
        String liveTitle,
        boolean liveRecorded
) {
}
