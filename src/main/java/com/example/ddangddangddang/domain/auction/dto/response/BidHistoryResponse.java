package com.example.ddangddangddang.domain.auction.dto.response;

import com.example.ddangddangddang.domain.auction.entity.Bid;
import com.example.ddangddangddang.domain.auction.entity.BidStatus;

import java.time.LocalDateTime;

/**
 * 경매의 입찰 기록 한 건. 누구나 볼 수 있어서 회원 번호는 내보내지 않고 가린 닉네임만 보여준다.
 *
 * @param bidderNickname 가린 닉네임 (예: "땅땅이" → "땅*이"). 탈퇴 등으로 회원을 찾지 못하면 "알 수 없음"
 * @param status         ACTIVE(최고 입찰)·OUTBID(밀림)·WON(낙찰). 상태 도입 전 입찰이면 null
 * @param mine           로그인한 사람 본인의 입찰인지. 로그인하지 않았으면 항상 false
 */
public record BidHistoryResponse(
        Long bidId,
        String bidderNickname,
        long amount,
        BidStatus status,
        boolean mine,
        LocalDateTime createdAt
) {

    public static BidHistoryResponse of(Bid bid, String maskedNickname, Long viewerId) {
        return new BidHistoryResponse(
                bid.getId(),
                maskedNickname,
                bid.getAmount(),
                bid.getStatus(),
                viewerId != null && viewerId.equals(bid.getBidderId()),
                bid.getCreatedAt()
        );
    }
}
