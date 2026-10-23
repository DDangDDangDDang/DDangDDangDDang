package com.example.ddangddangddang.domain.donation.dto;

import com.example.ddangddangddang.domain.donation.entity.SellerPerk;
import com.example.ddangddangddang.domain.donation.entity.SellerPerkType;

import java.time.LocalDateTime;

/** 산 혜택. startsAt ~ endsAt 동안 효과가 있다. (이어 붙여 산 혜택은 startsAt이 앞 혜택이 끝나는 시각) */
public record SellerPerkResponse(
        Long id,
        SellerPerkType type,
        long price,
        LocalDateTime startsAt,
        LocalDateTime endsAt
) {
    public static SellerPerkResponse from(SellerPerk perk) {
        return new SellerPerkResponse(perk.getId(), perk.getType(), perk.getPrice(), perk.getStartsAt(), perk.getEndsAt());
    }
}
