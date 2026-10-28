package com.example.ddangddangddang.domain.shop.entity;

import com.example.ddangddangddang.domain.user.entity.NicknameBadge;

/**
 * 마일리지 상점 상품. 가격은 고정이다. (관리자가 바꾸지 않음)
 * 상품마다 효과가 다르므로, 구매 API도 상품 종류별로 둔다. (닉네임 변경권은 새 닉네임을 함께 받는다)
 * 닉네임 뱃지는 영구로 가지며, 사면 바로 장착된다. (badge가 있는 상품)
 * 경매 위로 올리기는 판매자가 내 경매를 골라 산다. (AuctionBoost: 3시간, 동시 10개, 경매당 한 번)
 * 파워 판매자는 판매 확정 20건 이상인 인증 판매자만 영구로 한 번 산다. (열심히 판매했다는 표시)
 */
public enum ShopItem {

    NICKNAME_CHANGE("닉네임 변경권", "변경 제한 기간을 기다리지 않고 지금 바로 닉네임을 바꿔요. 바꾼 날부터 다시 30일 동안 바꿀 수 없어요.", 3_000, null),
    BADGE_ANGEL_WINGS("천사 날개 뱃지", "라이브 채팅에서 닉네임 양옆에 하얀 천사 날개가 달려요. 한 번 사면 계속 쓸 수 있어요.", 5_000, NicknameBadge.ANGEL_WINGS),
    BADGE_DEVIL_WINGS("악마 날개 뱃지", "라이브 채팅에서 닉네임 양옆에 붉은 악마 날개가 달려요. 한 번 사면 계속 쓸 수 있어요.", 5_000, NicknameBadge.DEVIL_WINGS),
    BADGE_HALO("천사 링 뱃지", "라이브 채팅에서 닉네임 위에 금빛 링이 떠요. 한 번 사면 계속 쓸 수 있어요.", 5_000, NicknameBadge.HALO),
    BADGE_STAR("별 뱃지", "라이브 채팅에서 닉네임 앞에 별이 붙고 금빛으로 빛나요. 한 번 사면 계속 쓸 수 있어요.", 5_000, NicknameBadge.STAR),
    AUCTION_BOOST("경매 위로 올리기", "내 경매를 3시간 동안 경매 목록 맨 위에 '홍보'로 올려요. 경매 하나는 한 번만 올릴 수 있어요.", 1_000, null),
    POWER_SELLER("파워 판매자", "판매자 프로필과 방송 카드에 '파워' 표시가 영구로 붙어요. 판매 확정 20건 이상인 인증 판매자만 살 수 있어요.", 20_000, null);

    private final String displayName;
    private final String description;
    private final long price;
    private final NicknameBadge badge;

    ShopItem(String displayName, String description, long price, NicknameBadge badge) {
        this.displayName = displayName;
        this.description = description;
        this.price = price;
        this.badge = badge;
    }

    /** 이 뱃지를 파는 상품 */
    public static ShopItem of(NicknameBadge badge) {
        for (ShopItem item : values()) {
            if (item.badge == badge) {
                return item;
            }
        }
        throw new IllegalArgumentException("상점에서 팔지 않는 뱃지입니다: " + badge);
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public long price() {
        return price;
    }

    /** 닉네임 뱃지 상품이면 그 뱃지, 아니면 null */
    public NicknameBadge badge() {
        return badge;
    }
}
