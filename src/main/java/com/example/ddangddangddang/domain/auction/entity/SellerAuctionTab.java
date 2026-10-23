package com.example.ddangddangddang.domain.auction.entity;

import java.util.List;

public enum SellerAuctionTab {
    ONGOING, //진행중(기본)
    READY, //시작전
    PAST; //지난 경매

    //includeFailedAndCanceled : 지난 경매 유찰,취소 볼지(기본은 낙찰만, 체크시 유찰,취소 전체 조회)
    public List<AuctionStatus> statuses(boolean includeFailedAndCanceled){
        return switch (this) {
            case ONGOING -> List.of(AuctionStatus.ONGOING);
            case READY -> List.of(AuctionStatus.READY);
            case PAST -> includeFailedAndCanceled
                    ? List.of(AuctionStatus.ENDED, AuctionStatus.FAILED,AuctionStatus.CANCELED)
            :List.of(AuctionStatus.ENDED);
        };

    }

}
