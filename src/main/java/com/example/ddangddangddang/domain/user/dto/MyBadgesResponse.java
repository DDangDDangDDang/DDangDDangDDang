package com.example.ddangddangddang.domain.user.dto;

import com.example.ddangddangddang.domain.user.entity.NicknameBadge;

import java.util.List;

/**
 * 내 닉네임 꾸미기
 *
 * @param owned         가진 꾸미기 (산 순서, 스킨·뱃지·색상을 함께 담는다)
 * @param equipped      장착한 채팅 스킨. 없으면 null
 * @param equippedTitle 장착한 채팅 뱃지(칭호). 없으면 null
 * @param equippedColor 장착한 닉네임 색상. 없으면 null
 */
public record MyBadgesResponse(List<NicknameBadge> owned, NicknameBadge equipped, NicknameBadge equippedTitle, NicknameBadge equippedColor) {
}
