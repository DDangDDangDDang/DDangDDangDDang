package com.example.ddangddangddang.domain.livestream.infra;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

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

    /**
     * 홈 미리보기 전용 토큰. 다른 참가자에게 보이지 않는 숨은 참가자로 입장하고, 송출할 수 없다.
     * 토큰은 짧게만 유효하다. (접속한 뒤에는 LiveKit이 연결을 유지한다)
     */
    String createPreviewToken(String roomName, String identity);

    /** 브라우저가 접속할 LiveKit 주소 */
    String getWsUrl();

    /**
     * 방별 시청자 수 (방 이름 → 시청자 수). identity가 viewerIdentityPrefix로 시작하는 참가자만 센다. (판매자 제외)
     * 미디어 서버에 없는 방(아직 아무도 접속하지 않았거나 이미 지워진 방)은 결과에 없다.
     */
    Map<String, Integer> getViewerCounts(Collection<String> roomNames, String viewerIdentityPrefix);

    /**
     * 판매자가 송출 중인 방 이름. identity가 publisherIdentityPrefix로 시작하는 참가자가 끄지 않은(음소거가 아닌) 트랙(영상·소리)을 하나 이상 보내고 있는 방만 담는다.
     * 미디어 서버에 없는 방(판매자가 아직 접속하지 않았거나 이미 지워진 방)은 송출 중이 아니다.
     */
    Set<String> getPublishingRooms(Collection<String> roomNames, String publisherIdentityPrefix);
}
