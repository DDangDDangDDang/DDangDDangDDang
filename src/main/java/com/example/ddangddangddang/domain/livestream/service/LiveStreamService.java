package com.example.ddangddangddang.domain.livestream.service;

import com.example.ddangddangddang.domain.livestream.dto.LiveStreamCreateRequest;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamResponse;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamTokenResponse;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.exception.LiveStreamErrorCode;
import com.example.ddangddangddang.domain.livestream.infra.MediaServerClient;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LiveStreamService {

    private final LiveStreamRepository liveStreamRepository;
    private final MediaServerClient mediaServerClient;

    // TODO: 판매자 권한(ROLE_SELLER) 확인은 인증 담당과 방식 확정 후 추가
    @Transactional
    public LiveStreamResponse create(Long sellerId, LiveStreamCreateRequest request) {
        LiveStream liveStream = liveStreamRepository.save(LiveStream.create(sellerId, request.title()));
        return LiveStreamResponse.from(liveStream);
    }

    public LiveStreamResponse get(Long liveStreamId) {
        return LiveStreamResponse.from(findById(liveStreamId));
    }

    @Transactional
    public LiveStreamTokenResponse start(Long liveStreamId, Long sellerId, String nickname) {
        LiveStream liveStream = findById(liveStreamId);

        if (liveStreamRepository.existsBySellerIdAndStatus(sellerId, LiveStreamStatus.LIVE)) {
            throw new BusinessException(LiveStreamErrorCode.ALREADY_LIVE);
        }

        liveStream.start(sellerId);
        // 외부 API 호출 전에 flush 해서 @Version 충돌(동시 시작 요청)을 먼저 걸러낸다.
        flushWithVersionCheck(liveStream);

        // 실패 시 예외 -> 트랜잭션 롤백 -> 상태는 SCHEDULED 그대로
        mediaServerClient.createRoom(liveStream.getRoomName());

        String token = mediaServerClient.createPublisherToken(
                liveStream.getRoomName(), publisherIdentity(sellerId), nickname);

        return new LiveStreamTokenResponse(
                liveStream.getId(), liveStream.getRoomName(), mediaServerClient.getWsUrl(), token);
    }

    public LiveStreamTokenResponse join(Long liveStreamId, Long memberId, String nickname) {
        LiveStream liveStream = findById(liveStreamId);
        liveStream.validateJoinable();

        String token = mediaServerClient.createViewerToken(
                liveStream.getRoomName(), viewerIdentity(memberId), nickname);

        return new LiveStreamTokenResponse(
                liveStream.getId(), liveStream.getRoomName(), mediaServerClient.getWsUrl(), token);
    }

    @Transactional
    public LiveStreamResponse end(Long liveStreamId, Long sellerId) {
        LiveStream liveStream = findById(liveStreamId);
        liveStream.end(sellerId);
        flushWithVersionCheck(liveStream);

        // 방을 삭제하면 접속 중인 판매자·시청자 모두 연결이 끊긴다.
        mediaServerClient.deleteRoom(liveStream.getRoomName());

        // TODO(6단계): LiveStreamEndedEvent 발행 -> 경매 도메인에서 진행 중 경매 처리
        return LiveStreamResponse.from(liveStream);
    }

    // 동시에 같은 방송 상태를 바꾸면 먼저 커밋된 요청만 성공하고, 나머지는 409 CONCURRENT_UPDATE로 응답한다.
    private void flushWithVersionCheck(LiveStream liveStream) {
        try {
            liveStreamRepository.saveAndFlush(liveStream);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(LiveStreamErrorCode.CONCURRENT_UPDATE, e);
        }
    }

    private LiveStream findById(Long liveStreamId) {
        return liveStreamRepository.findById(liveStreamId)
                .orElseThrow(() -> new BusinessException(LiveStreamErrorCode.NOT_FOUND));
    }

    private String publisherIdentity(Long sellerId) {
        return "seller-" + sellerId;
    }

    private String viewerIdentity(Long memberId) {
        return "viewer-" + memberId;
    }
}