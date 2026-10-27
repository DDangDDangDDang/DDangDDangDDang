package com.example.ddangddangddang.domain.auction.dto.response;

/**
 * 이 경매에서 내 입찰 상태. 경매 상세·입찰 창에서 "최고 입찰 중 / 밀렸어요 / 낙찰" 안내에 쓴다.
 *
 * @param state            NONE(입찰 안 함), LEADING(최고 입찰 중), OUTBID(밀림, 진행 중), WON(낙찰·즉시구매), LOST(낙찰받지 못하고 끝남)
 * @param myHighestAmount  내가 넣은 가장 높은 입찰 금액. 입찰한 적 없으면 null
 */
public record MyBidResponse(
        Long auctionId,
        State state,
        Long myHighestAmount
) {

    public enum State {
        NONE,
        LEADING,
        OUTBID,
        WON,
        LOST
    }
}
