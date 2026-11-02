package com.example.ddangddangddang.domain.user.dto;

import com.example.ddangddangddang.domain.user.entity.NicknameBadge;
import com.example.ddangddangddang.domain.user.entity.NicknameBadgeKind;

/**
 * 장착 변경. badge가 있으면 그 꾸미기를 장착하고, 없으면 kind 종류의 꾸미기를 뺀다.
 *
 * @param badge 장착할 꾸미기. 뺄 때는 null
 * @param kind  뺄 종류. 없으면 채팅 스킨을 뺀다
 */
public record BadgeEquipRequest(NicknameBadge badge, NicknameBadgeKind kind) {
}
