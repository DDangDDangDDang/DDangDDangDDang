package com.example.ddangddangddang.domain.shop.dto;

import com.example.ddangddangddang.domain.shop.entity.ShopAudience;
import com.example.ddangddangddang.domain.shop.entity.ShopItem;
import com.example.ddangddangddang.domain.user.entity.NicknameBadge;
import com.example.ddangddangddang.domain.user.entity.NicknameBadgeKind;

/**
 * 상점 상품 하나.
 *
 * @param audience          상점 화면의 탭 (구매자·판매자)
 * @param badge             닉네임 꾸미기 상품이면 그 꾸미기 (화면이 미리 보기를 그린다), 아니면 null
 * @param badgeKind         꾸미기 종류 (화면이 종류별로 묶어 펼친다), 꾸미기 상품이 아니면 null
 * @param available         지금 이 회원이 살 수 있는지 (마일리지 잔액은 보지 않는다. 화면이 지갑과 비교)
 * @param unavailableReason 살 수 없는 이유 (화면 문구). 살 수 있으면 null
 */
public record ShopItemResponse(
        ShopItem item,
        String name,
        String description,
        long price,
        ShopAudience audience,
        NicknameBadge badge,
        NicknameBadgeKind badgeKind,
        boolean available,
        String unavailableReason
) {

    public static ShopItemResponse available(ShopItem item) {
        return of(item, true, null);
    }

    public static ShopItemResponse unavailable(ShopItem item, String reason) {
        return of(item, false, reason);
    }

    private static ShopItemResponse of(ShopItem item, boolean available, String reason) {
        NicknameBadge badge = item.badge();
        return new ShopItemResponse(item, item.displayName(), item.description(), item.price(), item.audience(),
                badge, badge == null ? null : badge.kind(), available, reason);
    }
}
