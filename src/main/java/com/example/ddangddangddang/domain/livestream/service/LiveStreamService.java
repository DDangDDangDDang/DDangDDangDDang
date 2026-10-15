package com.example.ddangddangddang.domain.livestream.service;

import com.example.ddangddangddang.domain.livechat.moderation.ChatContentFilter;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamCreateRequest;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamResponse;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamTokenResponse;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamUpdateRequest;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.event.LiveStreamEndedEvent;
import com.example.ddangddangddang.domain.livestream.event.LiveStreamUpdatedEvent;
import com.example.ddangddangddang.domain.livestream.exception.LiveStreamErrorCode;
import com.example.ddangddangddang.domain.livestream.infra.MediaServerClient;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LiveStreamService {

    // 시청 입장 토큰의 LiveKit identity 앞부분. 시청자 수를 셀 때 판매자와 구분하는 데도 쓴다. (LiveViewerStatsCollector)
    public static final String VIEWER_IDENTITY_PREFIX = "viewer-";

    private final LiveStreamRepository liveStreamRepository;
    private final MediaServerClient mediaServerClient;
    private final ApplicationEventPublisher eventPublisher;
    private final ChatContentFilter contentFilter;

    /**
     * 방송을 만든다. 판매자는 종료되지 않은(대기 중·방송 중) 방송을 하나만 가질 수 있다.
     * 먼저 확인해 안내하고, 동시에 두 요청이 확인을 통과해도 DB 유니크 제약(active_seller_id)으로 하나만 저장된다.
     */
    // TODO: 판매자 권한(ROLE_SELLER) 확인은 인증 담당과 방식 확정 후 추가
    @Transactional
    public LiveStreamResponse create(Long sellerId, LiveStreamCreateRequest request) {
        String title = requireTitle(request.title());
        String description = blankToNull(request.description());
        validateContent(title);
        validateContent(description);
        if (liveStreamRepository.existsByActiveSellerId(sellerId)) {
            throw new BusinessException(LiveStreamErrorCode.ACTIVE_STREAM_EXISTS);
        }
        try {
            LiveStream liveStream = liveStreamRepository.saveAndFlush(LiveStream.create(sellerId, title, description));
            return LiveStreamResponse.from(liveStream);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(LiveStreamErrorCode.ACTIVE_STREAM_EXISTS, e);
        }
    }

    /**
     * 판매자의 종료되지 않은 방송. 없으면 null. (판매자 스튜디오에서 이어서 방송하기)
     */
    public LiveStreamResponse getMyActive(Long sellerId) {
        return liveStreamRepository.findByActiveSellerId(sellerId).map(LiveStreamResponse::from).orElse(null);
    }

    /**
     * 방송 정보(제목·설명)를 바꾼다. 대기 중이거나 방송 중일 때 방송 주인만 바꿀 수 있다.
     * 바뀐 값이 있으면 커밋 뒤 /topic/live-streams/{id} 구독자(시청자 화면)에게 알린다.
     */
    @Transactional
    public LiveStreamResponse update(Long liveStreamId, Long sellerId, LiveStreamUpdateRequest request) {
        LiveStream liveStream = findById(liveStreamId);
        String title = request.title() == null ? null : requireTitle(request.title());
        // null은 그대로 두기, 빈 문자열(공백만 포함)은 설명 지우기
        String description = request.description() == null ? null : request.description().strip();
        validateContent(title);
        validateContent(description);

        if (!liveStream.update(sellerId, title, description)) {
            return LiveStreamResponse.from(liveStream);
        }
        flushWithVersionCheck(liveStream);

        LiveStreamResponse response = LiveStreamResponse.from(liveStream);
        eventPublisher.publishEvent(new LiveStreamUpdatedEvent(response));
        return response;
    }

    // 판매자가 제목·설명에 연락처나 외부 링크를 적어 플랫폼 밖 거래로 유도하지 못하게 채팅과 같은 규칙으로 막는다.
    private void validateContent(String content) {
        contentFilter.findViolation(content).ifPresent(violation -> {
            throw new BusinessException(LiveStreamErrorCode.CONTENT_NOT_ALLOWED, violation.getMessage());
        });
    }

    /**
     * 저장할 값과 같은 기준(strip)으로 비었는지 본다.
     * 요청 검증(@NotBlank의 trim, 정규식 \S)은 전각 공백(U+2003) 같은 유니코드 공백만으로 된 제목을 통과시킬 수 있어
     * strip 뒤 빈 제목이 저장되지 않도록 여기서 한 번 더 막는다.
     */
    private static String requireTitle(String title) {
        String stripped = title == null ? "" : title.strip();
        if (stripped.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "방송 제목은 비워 둘 수 없습니다.");
        }
        return stripped;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
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

        return publisherTokenResponse(liveStream, sellerId, nickname);
    }

    /**
     * 송출 중인 방송의 송출 토큰을 다시 발급한다. 방송 상태와 LiveKit 방은 그대로 두고 토큰만 새로 만든다.
     * 판매자가 방송 중에 새로고침했거나, 송출 연결이 끊겼거나, 토큰이 만료됐을 때 다시 송출하기 위함이다.
     * 같은 identity로 다시 접속하므로, 이전 접속(다른 탭 등)이 남아 있으면 LiveKit이 이전 접속을 끊는다.
     */
    public LiveStreamTokenResponse reissuePublisherToken(Long liveStreamId, Long sellerId, String nickname) {
        LiveStream liveStream = findById(liveStreamId);
        liveStream.validatePublishable(sellerId);
        return publisherTokenResponse(liveStream, sellerId, nickname);
    }

    private LiveStreamTokenResponse publisherTokenResponse(LiveStream liveStream, Long sellerId, String nickname) {
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

        // 커밋 후 리스너가 처리한다. (채팅 종료 안내 / TODO(6단계): 경매 도메인에서 진행 중 경매 처리)
        eventPublisher.publishEvent(new LiveStreamEndedEvent(liveStream.getId()));
        return LiveStreamResponse.from(liveStream);
    }

    /**
     * 채팅처럼 방송 중에만 할 수 있는 작업 전에 호출한다.
     * 메시지마다 호출되므로 엔티티 전체를 읽지 않고 존재 여부만 확인한다.
     */
    public void validateLive(Long liveStreamId) {
        if (!liveStreamRepository.existsByIdAndStatus(liveStreamId, LiveStreamStatus.LIVE)) {
            throw new BusinessException(LiveStreamErrorCode.NOT_LIVE);
        }
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
        return VIEWER_IDENTITY_PREFIX + memberId;
    }
}