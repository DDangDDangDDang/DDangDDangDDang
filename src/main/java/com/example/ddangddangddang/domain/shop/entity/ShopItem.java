package com.example.ddangddangddang.domain.shop.entity;

import com.example.ddangddangddang.domain.user.entity.NicknameBadge;

/**
 * 마일리지 상점 상품. 가격은 고정이다. (관리자가 바꾸지 않음)
 * 상품마다 효과가 다르므로, 구매 API도 상품 종류별로 둔다. (닉네임 변경권은 새 닉네임을 함께 받는다)
 * 닉네임 꾸미기(채팅 스킨·채팅 뱃지·닉네임 색상)는 영구로 가지며, 사면 바로 장착된다. (badge가 있는 상품)
 * 경매 위로 올리기는 판매자가 내 경매를 골라 산다. (AuctionBoost: 3시간, 동시 10개, 경매당 한 번)
 * 파워 판매자는 판매 확정 20건 이상인 인증 판매자만 영구로 한 번 산다. (열심히 판매했다는 표시)
 * 상품 상수 이름은 구매 내역(ShopPurchase)에 저장되니 바꾸지 않는다. audience는 상점 화면의 탭이다.
 */
public enum ShopItem {

    NICKNAME_CHANGE("닉네임 변경권", "변경 제한 기간을 기다리지 않고 지금 바로 닉네임을 바꿔요. 바꾼 날부터 다시 30일 동안 바꿀 수 없어요.", 3_000, ShopAudience.BUYER, null),
    BADGE_ANGEL_WINGS("천사 날개", "라이브 채팅에서 닉네임 양옆에 하얀 천사 날개가 달려요.", 5_000, ShopAudience.BUYER, NicknameBadge.ANGEL_WINGS),
    BADGE_DEVIL_WINGS("악마 날개", "라이브 채팅에서 닉네임 양옆에 붉은 악마 날개가 달려요.", 5_000, ShopAudience.BUYER, NicknameBadge.DEVIL_WINGS),
    BADGE_HALO("천사 링", "라이브 채팅에서 닉네임 위에 금빛 링이 떠요.", 5_000, ShopAudience.BUYER, NicknameBadge.HALO),
    BADGE_STAR("별", "라이브 채팅에서 닉네임 앞에 별이 붙고 금빛으로 빛나요.", 5_000, ShopAudience.BUYER, NicknameBadge.STAR),
    TITLE_BID_KING("입찰왕", "라이브 채팅에서 닉네임 앞에 '입찰왕' 칭호가 붙어요.", 3_000, ShopAudience.BUYER, NicknameBadge.TITLE_BID_KING),
    TITLE_WIN_MASTER("낙찰 장인", "라이브 채팅에서 닉네임 앞에 '낙찰 장인' 칭호가 붙어요.", 3_000, ShopAudience.BUYER, NicknameBadge.TITLE_WIN_MASTER),
    TITLE_REGULAR("단골손님", "라이브 채팅에서 닉네임 앞에 '단골손님' 칭호가 붙어요.", 3_000, ShopAudience.BUYER, NicknameBadge.TITLE_REGULAR),
    TITLE_BIG_SPENDER("큰손", "라이브 채팅에서 닉네임 앞에 '큰손' 칭호가 붙어요.", 3_000, ShopAudience.BUYER, NicknameBadge.TITLE_BIG_SPENDER),
    COLOR_GOLD("금색", "라이브 채팅에서 닉네임이 금색으로 보여요.", 2_000, ShopAudience.BUYER, NicknameBadge.COLOR_GOLD),
    COLOR_MINT("민트", "라이브 채팅에서 닉네임이 민트색으로 보여요.", 2_000, ShopAudience.BUYER, NicknameBadge.COLOR_MINT),
    COLOR_CORAL("코랄", "라이브 채팅에서 닉네임이 코랄색으로 보여요.", 2_000, ShopAudience.BUYER, NicknameBadge.COLOR_CORAL),
    COLOR_PURPLE("보라", "라이브 채팅에서 닉네임이 보라색으로 보여요.", 2_000, ShopAudience.BUYER, NicknameBadge.COLOR_PURPLE),
    AUCTION_BOOST("경매 위로 올리기", "내 경매를 3시간 동안 경매 목록 맨 위에 '홍보'로 올려요. 경매 하나는 한 번만 올릴 수 있어요.", 1_000, ShopAudience.SELLER, null),
    POWER_SELLER("파워 판매자", "판매자 프로필과 방송 카드에 '파워' 표시가 영구로 붙어요. 판매 확정 20건 이상인 인증 판매자만 살 수 있어요.", 20_000, ShopAudience.SELLER, null);

    private final String displayName;
    private final String description;
    private final long price;
    private final ShopAudience audience;
    private final NicknameBadge badge;

    ShopItem(String displayName, String description, long price, ShopAudience audience, NicknameBadge badge) {
        this.displayName = displayName;
        this.description = description;
        this.price = price;
        this.audience = audience;
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

    public ShopAudience audience() {
        return audience;
    }

    /** 닉네임 꾸미기 상품이면 그 꾸미기, 아니면 null */
    public NicknameBadge badge() {
        return badge;
    }
}
