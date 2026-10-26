package com.example.ddangddangddang.domain.donation.dto;

import com.example.ddangddangddang.domain.donation.entity.SellerPerkType;
import jakarta.validation.constraints.NotNull;

/** 판매자 상점에서 살 혜택 */
public record SellerPerkPurchaseRequest(
        @NotNull(message = "살 상품을 골라 주세요.")
        SellerPerkType type
) {
}
