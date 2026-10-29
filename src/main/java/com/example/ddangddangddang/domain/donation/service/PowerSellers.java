package com.example.ddangddangddang.domain.donation.service;

import com.example.ddangddangddang.domain.donation.entity.SellerPerk;
import com.example.ddangddangddang.domain.donation.entity.SellerPerkType;
import com.example.ddangddangddang.domain.donation.repository.SellerPerkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;

/**
 * 파워 판매자인지. 방송 카드·판매자 프로필이 읽는다. (라이브 목록 맨 위 노출은 없앴다)
 * 파워 판매자는 마일리지 상점에서 영구 혜택(SellerPerk, endsAt = PERMANENT_END)을 산 판매자다. (ShopService가 grant로 만든다)
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PowerSellers {

    private final SellerPerkRepository perkRepository;

    public Set<Long> among(Collection<Long> sellerIds) {
        if (sellerIds.isEmpty()) {
            return Set.of();
        }
        return perkRepository.findActiveSellerIdsAmong(SellerPerkType.POWER_SELLER, LocalDateTime.now(), sellerIds);
    }

    public boolean is(Long sellerId) {
        return !among(Set.of(sellerId)).isEmpty();
    }

    /** 이미 파워 판매자 혜택을 산 적이 있는지 (영구라 한 번만 산다) */
    public boolean owns(Long sellerId) {
        return perkRepository.existsBySellerIdAndType(sellerId, SellerPerkType.POWER_SELLER);
    }

    /** 파워 판매자로 만든다 (영구). 마일리지 상점 결제와 같은 트랜잭션에서 부른다. (ShopService) */
    @Transactional
    public SellerPerk grant(Long sellerId, long price) {
        return perkRepository.save(SellerPerk.permanent(sellerId, SellerPerkType.POWER_SELLER, price, LocalDateTime.now()));
    }
}
