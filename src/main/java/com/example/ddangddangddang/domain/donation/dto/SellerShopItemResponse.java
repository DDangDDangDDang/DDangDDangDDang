package com.example.ddangddangddang.domain.donation.dto;

import com.example.ddangddangddang.domain.donation.entity.SellerPerkType;

/** 판매자 상점 상품. price는 받은 도네이션 포인트, periodDays는 효과가 있는 기간(일) */
public record SellerShopItemResponse(
        SellerPerkType type,
        String name,
        String description,
        long price,
        long periodDays
) {
}
