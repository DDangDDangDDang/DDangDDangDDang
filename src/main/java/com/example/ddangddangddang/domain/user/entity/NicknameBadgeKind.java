package com.example.ddangddangddang.domain.user.entity;

/** 닉네임 꾸미기 종류. 종류마다 하나씩 장착하므로 스킨·뱃지·색상을 함께 쓸 수 있다. */
public enum NicknameBadgeKind {
    SKIN,  // 채팅 스킨: 닉네임 둘레 장식
    TITLE, // 채팅 뱃지: 닉네임 앞에 붙는 작은 칭호
    COLOR  // 닉네임 색상
}
