package com.example.ddangddangddang.domain.livestream.infra;


import com.example.ddangddangddang.domain.livestream.exception.LiveStreamErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import retrofit2.Response;
import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanSubscribe;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import io.livekit.server.RoomServiceClient;

import java.io.IOException;

@Slf4j
@Component
@EnableConfigurationProperties(LiveKitProperties.class)
public class LiveKitMediaServerClient implements MediaServerClient {

    private final LiveKitProperties properties;
    private final RoomServiceClient roomServiceClient;

    public LiveKitMediaServerClient(LiveKitProperties properties) {
        this.properties = properties;
        this.roomServiceClient = RoomServiceClient.createClient(
                properties.apiUrl(), properties.apiKey(), properties.apiSecret());
    }

    @Override
    public void createRoom(String roomName) {
        try {
            Response<?> response = roomServiceClient.createRoom(roomName).execute();
            if (!response.isSuccessful()) {
                log.error("LiveKit 방 생성 실패 room={}, code={}", roomName, response.code());
                throw new BusinessException(LiveStreamErrorCode.MEDIA_SERVER_ERROR);
            }
        } catch (IOException e) {
            log.error("LiveKit 방 생성 통신 오류 room={}", roomName, e);
            throw new BusinessException(LiveStreamErrorCode.MEDIA_SERVER_ERROR, e);
        }
    }

    @Override
    public void deleteRoom(String roomName) {
        try {
            Response<?> response = roomServiceClient.deleteRoom(roomName).execute();
            // 이미 없는 방(404)은 종료 목적상 성공으로 간주
            if (!response.isSuccessful() && response.code() != 404) {
                log.error("LiveKit 방 삭제 실패 room={}, code={}", roomName, response.code());
                throw new BusinessException(LiveStreamErrorCode.MEDIA_SERVER_ERROR);
            }
        } catch (IOException e) {
            log.error("LiveKit 방 삭제 통신 오류 room={}", roomName, e);
            throw new BusinessException(LiveStreamErrorCode.MEDIA_SERVER_ERROR, e);
        }
    }

    @Override
    public String createPublisherToken(String roomName, String identity, String displayName) {
        AccessToken token = newToken(identity, displayName);
        token.addGrants(
                new RoomJoin(true),
                new RoomName(roomName),
                new CanPublish(true),
                new CanSubscribe(true),
                new CanPublishData(false)
        );
        return token.toJwt();
    }

    @Override
    public String createViewerToken(String roomName, String identity, String displayName) {
        AccessToken token = newToken(identity, displayName);
        token.addGrants(
                new RoomJoin(true),
                new RoomName(roomName),
                new CanPublish(false),      // 시청자는 영상 송출 불가
                new CanSubscribe(true),
                new CanPublishData(false)   // 채팅은 별도 WebSocket으로 처리 예정
        );
        return token.toJwt();
    }

    @Override
    public String getWsUrl() {
        return properties.wsUrl();
    }

    private AccessToken newToken(String identity, String displayName) {
        AccessToken token = new AccessToken(properties.apiKey(), properties.apiSecret());
        token.setIdentity(identity);
        token.setName(displayName);
        return token;
    }
}
