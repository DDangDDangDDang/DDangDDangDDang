package com.example.ddangddangddang.domain.user.dto;

import com.example.ddangddangddang.domain.user.entity.NicknameBadge;

/** 장착할 뱃지. null이면 뱃지를 뺀다. */
public record BadgeEquipRequest(NicknameBadge badge) {
}
