package com.example.ddangddangddang.domain.donation.service;

import com.example.ddangddangddang.domain.donation.entity.DonationLotUsage;
import com.example.ddangddangddang.domain.donation.entity.DonationLotUsageType;
import com.example.ddangddangddang.domain.donation.entity.DonationPointLot;
import com.example.ddangddangddang.domain.donation.entity.DonationWallet;
import com.example.ddangddangddang.domain.donation.exception.DonationErrorCode;
import com.example.ddangddangddang.domain.donation.repository.DonationLotUsageRepository;
import com.example.ddangddangddang.domain.donation.repository.DonationPointLotRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 적립 묶음에서 마일리지를 쓴다. (선물하기, 마일리지 상점 구매)
 * 구매·판매 적립이든 선물받은 것이든 구분하지 않고, 유효기간이 먼저 끝나는 묶음부터 쓴 뒤 어느 묶음에서 얼마씩 썼는지 남긴다.
 * 지갑을 잠근 트랜잭션 안에서 부른다.
 *
 * 쓰는 대상(선물·혜택)을 저장해야 사용 기록에 그 ID를 남길 수 있으므로 두 단계로 나눈다.
 * 1. reserve: 쓸 수 있는지 확인하고 쓸 묶음을 고른다. (모자라면 DONATION_002, 아직 아무것도 바꾸지 않음)
 * 2. 대상을 저장한 뒤 spend: 묶음에서 빼고, 사용 기록을 남기고, 지갑 잔액을 줄인다.
 */
@Component
@RequiredArgsConstructor
public class DonationLotSpender {

    private final DonationPointLotRepository lotRepository;
    private final DonationLotUsageRepository usageRepository;

    public List<DonationPointLot> reserve(Long userId, long amount, LocalDateTime now) {
        List<DonationPointLot> lots = lotRepository.findUsable(userId, now);
        long usable = lots.stream().mapToLong(DonationPointLot::getRemaining).sum();
        if (usable < amount) {
            throw new BusinessException(DonationErrorCode.INSUFFICIENT_BALANCE);
        }
        return lots;
    }

    public void spend(DonationWallet wallet, List<DonationPointLot> lots, long amount, DonationLotUsageType type, Long refId) {
        long left = amount;
        for (DonationPointLot lot : lots) {
            if (left == 0) {
                break;
            }
            long used = lot.use(left);
            if (used > 0) {
                usageRepository.save(new DonationLotUsage(type, refId, lot.getId(), used));
                left -= used;
            }
        }
        wallet.deduct(amount);
    }
}
