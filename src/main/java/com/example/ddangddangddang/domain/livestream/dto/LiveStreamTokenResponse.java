package com.example.ddangddangddang.domain.livestream.dto;

/**
 * 클라이언트가 LiveKit에 직접 접속할 때 필요한 정보.
 * 클라이언트는 serverUrl + token 으로 room.connect() 를 호출한다.
 */
public record LiveStreamTokenResponse(
        Long liveStreamId,
        String roomName,
        String serverUrl,
        String token
) {
}
