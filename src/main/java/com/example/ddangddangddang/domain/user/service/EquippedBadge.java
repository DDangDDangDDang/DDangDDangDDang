package com.example.ddangddangddang.domain.user.service;

import com.example.ddangddangddang.domain.user.entity.NicknameBadge;

/**
 * 회원이 장착한 꾸미기 (스킨·뱃지·색상, 없으면 null)와 지금 닉네임. 캐시에 null을 넣지 않도록 한 번 감싼다. (NicknameBadges)
 * nickname은 채팅에 보일 닉네임이다. 로그인 토큰의 닉네임은 바꾼 뒤에도 토큰이 만료될 때까지 옛 값이라 이 값을 먼저 쓴다.
 * 없으면 null (회원을 못 찾았거나, 닉네임이 들어가기 전에 캐시된 값)
 */
public record EquippedBadge(NicknameBadge badge, NicknameBadge title, NicknameBadge color, String nickname) {

    public static final EquippedBadge NONE = new EquippedBadge(null, null, null, null);
}
