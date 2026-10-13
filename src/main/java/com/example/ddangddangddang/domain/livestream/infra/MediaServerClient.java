package com.example.ddangddangddang.domain.livestream.infra;

/**
 * 미디어 서버(LiveKit) 연동 추상화.
 * 서비스가 LiveKit SDK에 직접 의존하지 않게 해서 테스트에서 쉽게 대체하고,
 * 나중에 미디어 서버를 바꿔도 서비스 코드는 그대로 두기 위함.
 */
public interface MediaServerClient {

    void createRoom(String roomName);

    void deleteRoom(String roomName);

    /** 송출 + 시청 권한 토큰 (판매자) */
    String createPublisherToken(String roomName, String identity, String displayName);

    /** 시청 전용 토큰 (구매자) */
    String createViewerToken(String roomName, String identity, String displayName);

    /** 브라우저가 접속할 LiveKit 주소 */
    String getWsUrl();
}
