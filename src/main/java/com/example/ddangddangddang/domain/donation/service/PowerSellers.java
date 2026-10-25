package com.example.ddangddangddang.domain.donation.service;

import com.example.ddangddangddang.domain.donation.entity.SellerPerkType;
import com.example.ddangddangddang.domain.donation.repository.SellerPerkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;

/**
 * 지금 파워 판매자인지. 다른 도메인(방송 목록·방송 카드·판매자 프로필)이 읽기만 한다.
 * 파워 판매자는 판매자 상점에서 받은 도네이션 포인트로 산 혜택(SellerPerk)이 진행 중인 판매자다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PowerSellers {

    private final SellerPerkRepository perkRepository;

    public Set<Long> all() {
        return perkRepository.findActiveSellerIds(SellerPerkType.POWER_SELLER, LocalDateTime.now());
    }

    public Set<Long> among(Collection<Long> sellerIds) {
        if (sellerIds.isEmpty()) {
            return Set.of();
        }
        return perkRepository.findActiveSellerIdsAmong(SellerPerkType.POWER_SELLER, LocalDateTime.now(), sellerIds);
    }

    public boolean is(Long sellerId) {
        return !among(Set.of(sellerId)).isEmpty();
    }
}
