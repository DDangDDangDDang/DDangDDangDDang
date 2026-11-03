package com.example.ddangddangddang.domain.user.entity;

/**
 * 닉네임 꾸미기: 라이브 방송 채팅에서 닉네임을 꾸미는 장식. 마일리지 상점에서 사면 영구로 가진다.
 * 여러 개를 가질 수 있고, 종류(NicknameBadgeKind)마다 하나씩 장착한다. (User.equippedBadge·equippedTitle·equippedColor)
 * DB에는 이름이 문자열로 들어가니, 이미 판 상품의 이름은 바꾸지 않는다. (화면 이름은 displayName)
 */
public enum NicknameBadge {
    ANGEL_WINGS("천사 날개", NicknameBadgeKind.SKIN),
    DEVIL_WINGS("악마 날개", NicknameBadgeKind.SKIN),
    HALO("천사 링", NicknameBadgeKind.SKIN),
    STAR("별", NicknameBadgeKind.SKIN),
    TITLE_BID_KING("입찰왕", NicknameBadgeKind.TITLE),
    TITLE_WIN_MASTER("낙찰 장인", NicknameBadgeKind.TITLE),
    TITLE_REGULAR("단골손님", NicknameBadgeKind.TITLE),
    TITLE_BIG_SPENDER("큰손", NicknameBadgeKind.TITLE),
    COLOR_GOLD("금색", NicknameBadgeKind.COLOR),
    COLOR_MINT("민트", NicknameBadgeKind.COLOR),
    COLOR_CORAL("코랄", NicknameBadgeKind.COLOR),
    COLOR_PURPLE("보라", NicknameBadgeKind.COLOR);

    private final String displayName;
    private final NicknameBadgeKind kind;

    NicknameBadge(String displayName, NicknameBadgeKind kind) {
        this.displayName = displayName;
        this.kind = kind;
    }

    public String displayName() {
        return displayName;
    }

    public NicknameBadgeKind kind() {
        return kind;
    }
}
