package com.example.ddangddangddang.domain.shop.entity;

/**
 * 마일리지 상점 상품. 가격은 고정이다. (관리자가 바꾸지 않음)
 * 상품마다 효과가 다르므로, 구매 API도 상품별로 둔다. (닉네임 변경권은 새 닉네임을 함께 받는다)
 */
public enum ShopItem {

    NICKNAME_CHANGE("닉네임 변경권", "변경 제한 기간을 기다리지 않고 지금 바로 닉네임을 바꿔요. 바꾼 날부터 다시 30일 동안 바꿀 수 없어요.", 3_000);

    private final String displayName;
    private final String description;
    private final long price;

    ShopItem(String displayName, String description, long price) {
        this.displayName = displayName;
        this.description = description;
        this.price = price;
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
}
