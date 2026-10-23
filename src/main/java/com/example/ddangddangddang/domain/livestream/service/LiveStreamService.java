package com.example.ddangddangddang.domain.livestream.service;

import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.livechat.moderation.ChatContentFilter;
import com.example.ddangddangddang.domain.seller.service.SellerVerification;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamCreateRequest;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamResponse;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamTokenResponse;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamUpdateRequest;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamHashtags;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.event.LiveStreamEndedEvent;
import com.example.ddangddangddang.domain.livestream.event.LiveStreamStartedEvent;
import com.example.ddangddangddang.domain.livestream.event.LiveStreamUpdatedEvent;
import com.example.ddangddangddang.domain.livestream.exception.LiveStreamErrorCode;
import com.example.ddangddangddang.domain.livestream.infra.MediaServerClient;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LiveStreamService {

    // 시청 입장 토큰의 LiveKit identity 앞부분. 시청자 수를 셀 때 판매자와 구분하는 데도 쓴다. (LiveViewerStatsCollector)
    public static final String VIEWER_IDENTITY_PREFIX = "viewer-";
    // 송출 토큰의 LiveKit identity 앞부분. 송출이 멈춘 방송을 찾을 때 쓴다. (LiveStreamIdleWatcher)
    public static final String PUBLISHER_IDENTITY_PREFIX = "seller-";
    // 로그인하지 않은 시청자. viewer-로 시작하므로 시청자 수에 함께 센다.
    static final String GUEST_IDENTITY_PREFIX = VIEWER_IDENTITY_PREFIX + "guest-";
    static final String GUEST_DISPLAY_NAME = "손님";

    // 판매자당 종료되지 않은 방송 하나 규칙의 유니크 제약 (LiveStream 엔티티의 @UniqueConstraint 이름)
    static final String ACTIVE_SELLER_CONSTRAINT = "uk_live_stream_active_seller";

    // 방송 시작 예정 시간을 정할 수 있는 범위 (지금부터)
    static final Duration SCHEDULE_LIMIT = Duration.ofDays(30);

    private final LiveStreamRepository liveStreamRepository;
    private final MediaServerClient mediaServerClient;
    private final ApplicationEventPublisher eventPublisher;
    private final ChatContentFilter contentFilter;
    // 방송 만들기·시작은 판매자 인증(관리자 승인)을 받은 회원만 할 수 있다.
    private final SellerVerification sellerVerification;
    // 영상 위에 띄울 경매가 이 방송에 올라간 경매인지 확인할 때만 쓴다. (경매 정보는 바꾸지 않음)
    private final AuctionRepository auctionRepository;

    /**
     * 방송을 만든다. 판매자는 종료되지 않은(대기 중·방송 중) 방송을 하나만 가질 수 있다.
     * 먼저 확인해 안내하고, 동시에 두 요청이 확인을 통과해도 DB 유니크 제약(active_seller_id)으로 하나만 저장된다.
     */
    // TODO: 판매자 권한(ROLE_SELLER) 확인은 인증 담당과 방식 확정 후 추가
    @Transactional
    public LiveStreamResponse create(Long sellerId, LiveStreamCreateRequest request) {
        // 방송은 판매자 인증(관리자 승인)을 받은 회원만 만들 수 있다. (시작할 때도 다시 확인)
        if (!sellerVerification.isVerified(sellerId)) {
            throw new BusinessException(LiveStreamErrorCode.NOT_VERIFIED_SELLER);
        }
        String title = requireTitle(request.title());
        String description = blankToNull(request.description());
        List<String> hashtags = LiveStreamHashtags.normalize(request.hashtags());
        validateContent(title);
        validateContent(description);
        validateHashtags(hashtags);
        validateScheduledStartAt(request.scheduledStartAt(), LocalDateTime.now());
        if (liveStreamRepository.existsByActiveSellerId(sellerId)) {
            throw new BusinessException(LiveStreamErrorCode.ACTIVE_STREAM_EXISTS);
        }
        try {
            LiveStream created = LiveStream.create(sellerId, title, description, request.scheduledStartAt());
            created.changeHashtags(hashtags);
            LiveStream liveStream = liveStreamRepository.saveAndFlush(created);
            return LiveStreamResponse.from(liveStream);
        } catch (DataIntegrityViolationException e) {
            // 판매자당 하나 규칙의 유니크 제약에 걸린 경우만 안내로 바꾼다. 다른 제약 위반(버그)은 그대로 드러나게 다시 던진다.
            if (isViolationOf(e, ACTIVE_SELLER_CONSTRAINT)) {
                throw new BusinessException(LiveStreamErrorCode.ACTIVE_STREAM_EXISTS, e);
            }
            throw e;
        }
    }

    /**
     * 원인 중 Hibernate 제약 위반 예외의 제약 이름이 constraint인지 확인한다.
     * DB마다 이름에 테이블 이름이 붙거나(MySQL: live_streams.uk_...) 대문자로 바뀌어(H2) 나오므로, 대소문자 없이 포함 여부로 본다.
     */
    static boolean isViolationOf(Throwable exception, String constraint) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                return name != null && name.toLowerCase(Locale.ROOT).contains(constraint);
            }
        }
        return false;
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
        String notice = request.notice() == null ? null : request.notice().strip();
        // null은 그대로 두기, 빈 목록은 해시태그 지우기
        List<String> hashtags = request.hashtags() == null ? null : LiveStreamHashtags.normalize(request.hashtags());
        validateContent(title);
        validateContent(description);
        validateContent(notice);
        validateHashtags(hashtags);
        validateScheduledStartAt(request.scheduledStartAt(), LocalDateTime.now());

        boolean changed = liveStream.update(sellerId, title, description, request.scheduledStartAt());
        // update가 주인·종료 여부를 확인한 뒤에 공지·해시태그를 바꾼다.
        if (notice != null) {
            changed = liveStream.changeNotice(notice) || changed;
        }
        if (hashtags != null) {
            changed = liveStream.changeHashtags(hashtags) || changed;
        }
        if (!changed) {
            return LiveStreamResponse.from(liveStream);
        }
        flushWithVersionCheck(liveStream);

        LiveStreamResponse response = LiveStreamResponse.from(liveStream);
        eventPublisher.publishEvent(new LiveStreamUpdatedEvent(response));
        return response;
    }

    /**
     * 영상 위에 띄울 경매를 정한다. auctionId가 null이면 내린다. 한 번에 하나만 띄우고, 새로 띄우면 이전 경매는 내려간다.
     * 이 방송에 올라간 시작 전·진행 중 경매만 띄울 수 있다. 바뀌면 커밋 뒤 시청자 화면에 방송 정보 변경으로 알린다.
     */
    @Transactional
    public LiveStreamResponse changeFeaturedAuction(Long liveStreamId, Long sellerId, Long auctionId) {
        LiveStream liveStream = findById(liveStreamId);
        // 주인이 아닌 사람에게는 경매가 있는지 알려 주지 않고 권한 오류를 먼저 낸다. (엔티티에서도 다시 확인)
        if (!liveStream.isOwner(sellerId)) {
            throw new BusinessException(LiveStreamErrorCode.NOT_OWNER);
        }
        if (auctionId != null && !auctionRepository.existsByIdAndLiveStreamIdAndStatusIn(
                auctionId, liveStreamId, List.of(AuctionStatus.READY, AuctionStatus.ONGOING))) {
            throw new BusinessException(LiveStreamErrorCode.FEATURED_AUCTION_NOT_AVAILABLE);
        }
        if (!liveStream.changeFeaturedAuction(sellerId, auctionId)) {
            return LiveStreamResponse.from(liveStream);
        }
        flushWithVersionCheck(liveStream);

        LiveStreamResponse response = LiveStreamResponse.from(liveStream);
        eventPublisher.publishEvent(new LiveStreamUpdatedEvent(response));
        return response;
    }

    /**
     * 방송 시작 예정 시간은 지금 이후, 지금부터 SCHEDULE_LIMIT(30일) 이내여야 한다. 정하지 않았으면(null) 검사하지 않는다.
     * 너무 먼 예정은 홈의 방송 예정 목록을 오래 차지하고, 판매자당 종료되지 않은 방송 하나 규칙 때문에 그동안 다른 방송도 못 하게 된다.
     */
    static void validateScheduledStartAt(LocalDateTime scheduledStartAt, LocalDateTime now) {
        if (scheduledStartAt == null) {
            return;
        }
        if (scheduledStartAt.isBefore(now)) {
            throw new BusinessException(LiveStreamErrorCode.INVALID_SCHEDULED_START, "방송 시작 예정 시간은 지금 이후로 정해 주세요.");
        }
        if (scheduledStartAt.isAfter(now.plus(SCHEDULE_LIMIT))) {
            throw new BusinessException(LiveStreamErrorCode.INVALID_SCHEDULED_START);
        }
    }

    // 판매자가 제목·설명에 연락처나 외부 링크를 적어 플랫폼 밖 거래로 유도하지 못하게 채팅과 같은 규칙으로 막는다.
    private void validateContent(String content) {
        contentFilter.findViolation(content).ifPresent(violation -> {
            throw new BusinessException(LiveStreamErrorCode.CONTENT_NOT_ALLOWED, violation.getMessage());
        });
    }

    // 해시태그도 제목·설명처럼 막힌 내용(연락처 등)을 넣을 수 없다. 태그를 이어 붙여 한 번에 검사한다.
    private void validateHashtags(List<String> hashtags) {
        if (hashtags != null && !hashtags.isEmpty()) {
            validateContent(String.join(" ", hashtags));
        }
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
        // 남의 방송이면 인증 여부보다 권한 오류를 먼저 알린다. (엔티티의 start에서도 다시 확인)
        if (liveStream.isOwner(sellerId) && !sellerVerification.isVerified(sellerId)) {
            throw new BusinessException(LiveStreamErrorCode.NOT_VERIFIED_SELLER);
        }

        if (liveStreamRepository.existsBySellerIdAndStatus(sellerId, LiveStreamStatus.LIVE)) {
            throw new BusinessException(LiveStreamErrorCode.ALREADY_LIVE);
        }

        liveStream.start(sellerId);
        // 외부 API 호출 전에 flush 해서 @Version 충돌(동시 시작 요청)을 먼저 걸러낸다.
        flushWithVersionCheck(liveStream);

        // 실패 시 예외 -> 트랜잭션 롤백 -> 상태는 SCHEDULED 그대로
        mediaServerClient.createRoom(liveStream.getRoomName());

        LiveStreamTokenResponse token = publisherTokenResponse(liveStream, sellerId, nickname);
        // 커밋 뒤 방송 예정 화면을 보던 시청자에게 알린다. 방송 정보 변경(LIVE)으로 새로고침 없이 영상에 접속하고, 채팅에 시작 안내가 올라간다.
        eventPublisher.publishEvent(new LiveStreamUpdatedEvent(LiveStreamResponse.from(liveStream)));
        eventPublisher.publishEvent(new LiveStreamStartedEvent(liveStream.getId()));
        return token;
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

    /**
     * 로그인하지 않은 사람의 시청 입장. 접속마다 다른 identity("viewer-guest-{무작위}")로 토큰을 만든다.
     * LiveKit은 같은 identity로 다시 접속하면 이전 접속을 끊으므로, 손님끼리 겹치지 않게 한다.
     */
    public LiveStreamTokenResponse joinAsGuest(Long liveStreamId) {
        LiveStream liveStream = findById(liveStreamId);
        liveStream.validateJoinable();

        String identity = GUEST_IDENTITY_PREFIX + UUID.randomUUID();
        String token = mediaServerClient.createViewerToken(liveStream.getRoomName(), identity, GUEST_DISPLAY_NAME);

        return new LiveStreamTokenResponse(
                liveStream.getId(), liveStream.getRoomName(), mediaServerClient.getWsUrl(), token);
    }

    @Transactional
    public LiveStreamResponse end(Long liveStreamId, Long sellerId) {
        return end(liveStreamId, sellerId, false);
    }

    /** 판매자 송출이 오래 없어 서버가 종료한다. (LiveStreamIdleWatcher) 채팅 종료 안내에 이유를 함께 알린다. */
    @Transactional
    public LiveStreamResponse endForIdle(Long liveStreamId, Long sellerId) {
        return end(liveStreamId, sellerId, true);
    }

    private LiveStreamResponse end(Long liveStreamId, Long sellerId, boolean idle) {
        LiveStream liveStream = findById(liveStreamId);
        // 자동 종료는 방송 중일 때만 한다. 감시가 겹쳐(잠금 만료 등) 이미 끝난 방송을 다시 끝내면 방 삭제·종료 안내가 두 번 나가므로 막는다.
        if (idle && liveStream.getStatus() != LiveStreamStatus.LIVE) {
            throw new BusinessException(LiveStreamErrorCode.NOT_LIVE);
        }
        liveStream.end(sellerId);
        flushWithVersionCheck(liveStream);

        // 방을 삭제하면 접속 중인 판매자·시청자 모두 연결이 끊긴다.
        mediaServerClient.deleteRoom(liveStream.getRoomName());

        // 커밋 후 리스너가 처리한다. (채팅 종료 안내 / TODO(6단계): 경매 도메인에서 진행 중 경매 처리)
        eventPublisher.publishEvent(new LiveStreamEndedEvent(liveStream.getId(), idle));
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
        return PUBLISHER_IDENTITY_PREFIX + sellerId;
    }

    private String viewerIdentity(Long memberId) {
        return VIEWER_IDENTITY_PREFIX + memberId;
    }
}