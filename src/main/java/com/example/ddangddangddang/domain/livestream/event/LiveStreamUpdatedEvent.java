package com.example.ddangddangddang.domain.livestream.event;

import com.example.ddangddangddang.domain.livestream.dto.LiveStreamResponse;

/**
 * 방송 정보(제목·설명)가 바뀌어 커밋된 뒤, 보고 있는 시청자 화면에 반영하기 위한 이벤트.
 */
public record LiveStreamUpdatedEvent(LiveStreamResponse liveStream) {
}
