package com.example.ddangddangddang.domain.notification.dto;

import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;

import java.util.List;

/** 알림 받는 경매 목록의 보기 */
public enum AuctionAlertFilter {
    ACTIVE(List.of(AuctionStatus.READY, AuctionStatus.ONGOING)),   // 시작 전·진행 중
    ENDED(List.of(AuctionStatus.ENDED, AuctionStatus.FAILED, AuctionStatus.CANCELED)); // 낙찰·유찰·취소

    private final List<AuctionStatus> statuses;

    AuctionAlertFilter(List<AuctionStatus> statuses) {
        this.statuses = statuses;
    }

    public List<AuctionStatus> statuses() {
        return statuses;
    }
}
