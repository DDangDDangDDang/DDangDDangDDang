package com.example.ddangddangddang.domain.livebot.dto;

import java.time.LocalDateTime;

/**
 * 관리자 라이브 봇 목록의 한 줄.
 * userId: 봇 계정 (판매자 프로필 주소에 쓴다), nickname: 봇 계정 닉네임 (계정을 찾지 못하면 null)
 * profileImageUrl: 봇 프로필 사진 주소 (없으면 null → 화면은 닉네임 첫 글자)
 * liveStreamId·liveTitle·liveStartedAt: 지금 켜 둔 방송. 꺼져 있으면 셋 다 null
 */
public record LiveBotResponse(
        Long id,
        Long userId,
        String nickname,
        String profileImageUrl,
        String videoUrl,
        Long liveStreamId,
        String liveTitle,
        LocalDateTime liveStartedAt,
        LocalDateTime createdAt
) {
}
