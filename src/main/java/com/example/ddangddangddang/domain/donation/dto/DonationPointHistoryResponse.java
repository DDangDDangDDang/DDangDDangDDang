package com.example.ddangddangddang.domain.donation.dto;

import com.example.ddangddangddang.domain.donation.entity.DonationPointHistory;
import com.example.ddangddangddang.domain.donation.entity.DonationPointHistoryType;

import java.time.LocalDateTime;

/**
 * 도네이션 포인트 내역 한 줄. amount는 항상 양수이고 늘었는지 줄었는지는 type으로 본다.
 * - EARN(구매 적립, refId=주문), SALE(판매 적립, refId=주문), GIFT(선물함, refId=선물), RECEIVE(선물받음, refId=선물),
 *   EXPIRE(유효기간 만료, refId=적립 묶음), SPEND(판매자 상점, refId=혜택)
 * balanceAfter: 이 변동 뒤의 마일리지
 */
public record DonationPointHistoryResponse(
        Long id,
        DonationPointHistoryType type,
        long amount,
        long balanceAfter,
        Long refId,
        LocalDateTime createdAt
) {
    public static DonationPointHistoryResponse from(DonationPointHistory history) {
        return new DonationPointHistoryResponse(history.getId(), history.getType(), history.getAmount(),
                history.getBalanceAfter(), history.getRefId(), history.getCreatedAt());
    }
}
