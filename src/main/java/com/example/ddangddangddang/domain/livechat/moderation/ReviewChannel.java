package com.example.ddangddangddang.domain.livechat.moderation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI 검토 대상 메시지가 오간 곳. description은 AI에 보내는 요청 문장에 들어간다.
 */
@Getter
@RequiredArgsConstructor
public enum ReviewChannel {

    LIVE_STREAM("라이브 경매 방송의 시청자 채팅과 선물 한마디"),
    DIRECT_MESSAGE("구매자와 판매자 사이의 1:1 메시지");

    private final String description;
}
