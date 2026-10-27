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
import livekit.LivekitModels;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /**
     * 방 목록으로 참가자가 있는 방만 고른 뒤, 그 방들의 참가자 중 시청자 identity로 접속한 사람을 센다.
     * "참가자 수 - 송출자 수"로 세면 판매자가 접속만 하고 카메라를 켜지 않았을 때 판매자가 시청자로 세어지므로 identity로 구분한다.
     */
    @Override
    public Map<String, Integer> getViewerCounts(Collection<String> roomNames, String viewerIdentityPrefix) {
        if (roomNames.isEmpty()) {
            return Map.of();
        }
        try {
            List<LivekitModels.Room> rooms = body(roomServiceClient.listRooms(List.copyOf(roomNames)).execute(), "방 목록 조회");
            Map<String, Integer> counts = new HashMap<>();
            for (LivekitModels.Room room : rooms) {
                if (room.getNumParticipants() == 0) {
                    counts.put(room.getName(), 0);
                    continue;
                }
                List<LivekitModels.ParticipantInfo> participants =
                        body(roomServiceClient.listParticipants(room.getName()).execute(), "참가자 조회");
                int viewers = (int) participants.stream()
                        .filter(participant -> participant.getIdentity().startsWith(viewerIdentityPrefix))
                        .count();
                counts.put(room.getName(), viewers);
            }
            return counts;
        } catch (IOException e) {
            log.error("LiveKit 시청자 수 조회 통신 오류 count={}", roomNames.size(), e);
            throw new BusinessException(LiveStreamErrorCode.MEDIA_SERVER_ERROR, e);
        }
    }

    @Override
    public Set<String> getPublishingRooms(Collection<String> roomNames, String publisherIdentityPrefix) {
        if (roomNames.isEmpty()) {
            return Set.of();
        }
        try {
            List<LivekitModels.Room> rooms = body(roomServiceClient.listRooms(List.copyOf(roomNames)).execute(), "방 목록 조회");
            Set<String> publishing = new HashSet<>();
            for (LivekitModels.Room room : rooms) {
                // 아무도 없거나 트랙을 보내는 참가자가 없는 방은 참가자를 조회하지 않는다.
                if (room.getNumParticipants() == 0 || room.getNumPublishers() == 0) {
                    continue;
                }
                List<LivekitModels.ParticipantInfo> participants =
                        body(roomServiceClient.listParticipants(room.getName()).execute(), "참가자 조회");
                // 카메라·마이크를 끈(음소거) 트랙은 송출로 보지 않는다.
                boolean sellerPublishing = participants.stream()
                        .filter(participant -> participant.getIdentity().startsWith(publisherIdentityPrefix))
                        .flatMap(participant -> participant.getTracksList().stream())
                        .anyMatch(track -> !track.getMuted());
                if (sellerPublishing) {
                    publishing.add(room.getName());
                }
            }
            return publishing;
        } catch (IOException e) {
            log.error("LiveKit 송출 상태 조회 통신 오류 count={}", roomNames.size(), e);
            throw new BusinessException(LiveStreamErrorCode.MEDIA_SERVER_ERROR, e);
        }
    }

    private <T> List<T> body(Response<List<T>> response, String action) {
        if (!response.isSuccessful() || response.body() == null) {
            log.error("LiveKit {} 실패 code={}", action, response.code());
            throw new BusinessException(LiveStreamErrorCode.MEDIA_SERVER_ERROR);
        }
        return response.body();
    }

    private AccessToken newToken(String identity, String displayName) {
        AccessToken token = new AccessToken(properties.apiKey(), properties.apiSecret());
        token.setIdentity(identity);
        token.setName(displayName);
        return token;
    }
}
