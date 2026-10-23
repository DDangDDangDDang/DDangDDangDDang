package com.example.ddangddangddang.domain.donation.event;

/**
 * 선물이 커밋된 뒤 방송 화면에 알린다. ("누구님이 몇 P를 선물했어요" 이펙트, 채팅 안내)
 * message는 선물과 함께 보낸 한마디 (없으면 null)
 */
public record DonationSentEvent(Long donationId, Long liveStreamId, Long giverId, String giverNickname, long amount, String message) {
}
