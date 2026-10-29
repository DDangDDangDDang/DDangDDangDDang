package com.example.ddangddangddang.domain.user.entity;

/**
 * 닉네임 뱃지: 라이브 방송 채팅에서 닉네임을 꾸미는 장식. 마일리지 상점에서 사면 영구로 가진다.
 * 여러 개를 가질 수 있고, 한 번에 하나만 장착한다. (User.equippedBadge)
 */
public enum NicknameBadge {
    ANGEL_WINGS("천사 날개"),
    DEVIL_WINGS("악마 날개"),
    HALO("천사 링"),
    STAR("별");

    private final String displayName;

    NicknameBadge(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
