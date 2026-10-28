package com.example.ddangddangddang.domain.shop.dto;

import com.example.ddangddangddang.domain.shop.entity.ShopItem;

/**
 * 상점 상품 하나.
 *
 * @param available         지금 이 회원이 살 수 있는지 (마일리지 잔액은 보지 않는다. 화면이 지갑과 비교)
 * @param unavailableReason 살 수 없는 이유 (화면 문구). 살 수 있으면 null
 */
public record ShopItemResponse(
        ShopItem item,
        String name,
        String description,
        long price,
        boolean available,
        String unavailableReason
) {

    public static ShopItemResponse available(ShopItem item) {
        return new ShopItemResponse(item, item.displayName(), item.description(), item.price(), true, null);
    }

    public static ShopItemResponse unavailable(ShopItem item, String reason) {
        return new ShopItemResponse(item, item.displayName(), item.description(), item.price(), false, reason);
    }
}
