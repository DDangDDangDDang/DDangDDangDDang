package com.example.ddangddangddang.domain.user.dto;

import com.example.ddangddangddang.domain.user.entity.NicknameBadge;

import java.util.List;

/**
 * 내 닉네임 뱃지
 *
 * @param owned    가진 뱃지 (산 순서)
 * @param equipped 장착한 뱃지. 없으면 null
 */
public record MyBadgesResponse(List<NicknameBadge> owned, NicknameBadge equipped) {
}
