package com.example.ddangddangddang.domain.user.service;

import com.example.ddangddangddang.domain.user.entity.NicknameBadge;

/** 회원이 장착한 뱃지 (없으면 badge가 null). 캐시에 null을 넣지 않도록 한 번 감싼다. (NicknameBadges) */
public record EquippedBadge(NicknameBadge badge) {
}
