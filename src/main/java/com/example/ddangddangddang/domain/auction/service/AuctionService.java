package com.example.ddangddangddang.domain.auction.service;

import java.util.stream.Collectors;
import java.util.Map;

import com.example.ddangddangddang.domain.auction.repository.AuctionViewRepository;
import com.example.ddangddangddang.domain.hammer.entity.SanctionType;
import com.example.ddangddangddang.domain.hammer.service.SanctionService;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import com.example.ddangddangddang.domain.order.entity.OrderStatus;
import com.example.ddangddangddang.domain.order.entity.OrderCancelReason;
import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.auction.dto.request.AuctionCreateRequest;
import com.example.ddangddangddang.domain.auction.dto.request.AuctionSearchCondition;
import com.example.ddangddangddang.domain.auction.dto.request.LiveAuctionCreateRequest;
import com.example.ddangddangddang.domain.auction.dto.response.AuctionListResponse;
import com.example.ddangddangddang.domain.auction.dto.response.AuctionResponse;
import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.event.AuctionCanceledEvent;
import com.example.ddangddangddang.domain.auction.event.LiveAuctionEvent;
import com.example.ddangddangddang.domain.auction.exception.AuctionErrorCode;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.exception.LiveStreamErrorCode;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.domain.product.entity.Status;
import com.example.ddangddangddang.domain.product.exception.ProductErrorCode;
import com.example.ddangddangddang.domain.product.repository.ProductRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.example.ddangddangddang.global.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.ddangddangddang.domain.seller.service.SellerVerification;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
@Transactional
public class AuctionService {
    private final AuctionRepository auctionRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final LiveStreamRepository liveStreamRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final FileStorage fileStorage;
    private final SanctionService sanctionService;
    private final SellerVerification sellerVerification;
    private final AuctionViewRepository auctionViewRepository;

    //같은 사람이 이 시간 안에 다시 보면 조회수를 세지 않는다
    private static final Duration VIEW_DEDUPLICATION_WINDOW = Duration.ofMinutes(30);

    //경매(일반) 생성 : 본인 상품이고 판매 중이며 대기 or 진행 중인 경매가 아닐때 생성이 가능합니다
    public AuctionResponse auctionCreate(Long sellerId, AuctionCreateRequest request) {
        //삼품 검증(공통)
        Product product=productVerification(sellerId,request.productId());
        // 미발송 판매 취소가 반복된 회원은 경매를 등록할 수 없다 (상품을 잠근 뒤에 읽는다. createLiveAuction 참고)
        sanctionService.requireNotBanned(sellerId, SanctionType.LISTING_BAN);

        // 판매자 인증(관리자 승인)을 받은 회원만 경매를 등록할 수 있다
        requireVerifiedSeller(sellerId);

        //시작 시간< 종료 시간, 종료 시간은 지금 이후, 진행 시간·시작 시각 범위
        scheduleVerification(request.startAt(), request.endAt(), LocalDateTime.now());

        //가격 규칙: 시작가·입찰 단위는 둘 다 있거나 둘 다 없음(즉시구매 전용), 즉시구매가 > 시작가
        priceVerification(request.startPrice(), request.bidUnit(), request.buyNowPrice());
        //저장(일반 경매라 라이브 null)
        Auction save = auctionRepository.save(new Auction(product, null, request.startPrice(), request.bidUnit(), request.buyNowPrice(), request.startAt(), request.endAt()));

        return AuctionResponse.from(save, this::imageUrl);

    }

    //경매 상세 조회
    @Transactional(readOnly = true)
    public AuctionResponse findAddBy(Long auctionId, Long userId, String ip) {
        Auction auction = auctionRepository.findById(auctionId).orElseThrow(
                () -> new BusinessException(AuctionErrorCode.NOT_FOUND)
        );
        countView(auction,userId,ip);
        return AuctionResponse.from(auction, this::imageUrl);
    }

    /**
     * 조회수 세기 (인기 경매 점수에 사용된다)
     * - 끝난 경매는 세지 않는다 (인기 목록에서 빠진 경매라 셀 필요가 없다)
     * - 판매자가 자기 경매를 본 건 세지 않는다 (자기 경매는 살 수 없으니 구매 관심에서 제외로 조회수에서는 제외 된다)
     * - Redis가 고장 나도 경매 상세는 보여야 하므로, 실패하면 경고 로그만 남긴다.
     */
    private void countView(Auction auction, Long userId, String ip){
        if (auction.getStatus() != AuctionStatus.READY && auction.getStatus() != AuctionStatus.ONGOING){
            return;
        }
        if (userId != null && userId.equals(auction.getProduct().getSeller().getId())){
            return;
        }
        // 로그인했으면 회원 ID로, 안 했으면 IP로 같은 사람을 구분한다.
        String viewer = userId != null ? "user:" + userId : "ip:" + ip;
        try {
            auctionViewRepository.increaseIfFirstView(auction.getId(),viewer,VIEW_DEDUPLICATION_WINDOW);
        } catch (RuntimeException e){
            log.warn("경매 조회수 기록 실패 auctionId={}",auction.getId(), e);
        }
    }

    //경매 취소(본인 경매이며 대기OR진행 중이며 입찰이 0건일시에만 취소가 가능합니다)
    public void cancelAuction(Long sellerId, Long auctionId) {

        //경매가 존재 하는지. 즉시구매·입찰·마감과 같은 순간이면 먼저 잠근 쪽이 처리되고,
        //늦은 쪽은 바뀐 상태를 보고 "취소할 수 없는 상태"로 거절된다 (잠그지 않으면 저장 충돌로 알 수 없는 오류가 난다)
        Auction auction = auctionRepository.findByIdForUpdate(auctionId).orElseThrow(
                () -> new BusinessException(AuctionErrorCode.NOT_FOUND)
        );

        //본인 경매인지
        if (!auction.getProduct().getSeller().getId().equals(sellerId)) {
            throw new BusinessException(AuctionErrorCode.NOT_OWNER);
        }

        //경매 상태가 READY OR ONGOING 인지 검증
        if (auction.getStatus() != AuctionStatus.READY && auction.getStatus() != AuctionStatus.ONGOING) {
            throw new BusinessException(AuctionErrorCode.NOT_CANCELABLE_STATUS);
        }

        //입찰 건수가 1건이라도 있는지
        if (auction.getBidCount() > 0) {
            throw new BusinessException(AuctionErrorCode.HAS_BIDS);
        }

        auction.cancel();
        flushWithVersionCheck(auction);
        eventPublisher.publishEvent(new AuctionCanceledEvent(auction.getId()));
        //방송에 올라간 경매면 시청 화면의 경매 목록을 갱신
        if (auction.getLiveStream() != null) {
            eventPublisher.publishEvent(LiveAuctionEvent.of(auction, LiveAuctionEvent.Type.CHANGED));
        }

    }

    // 라이브 중 경매 생성 : 방송 주인이 라이브 중 경매 생성,생성 즉시 AuctionStatus는 ONGOING변환
    public AuctionResponse createLiveAuction (Long sellerId, Long liveId,LiveAuctionCreateRequest request){
        return createLiveAuction(sellerId, liveId, request, true);
    }

    /**
     * 방송 봇의 경매 생성 (LiveBotSellingService 전용). 봇 계정은 관리자가 만들고 운영해 판매자 인증이 없으므로 인증을 보지 않는다.
     * 회원 요청에서는 부르지 않는다.
     */
    public AuctionResponse createLiveAuctionAsBot(Long botUserId, Long liveId, LiveAuctionCreateRequest request) {
        return createLiveAuction(botUserId, liveId, request, false);
    }

    private AuctionResponse createLiveAuction(Long sellerId, Long liveId, LiveAuctionCreateRequest request, boolean requireVerified){
        // 상품 검증(공통) - 반드시 이 트랜잭션의 첫 조회여야 한다 (동시성)
        // productVerification은 상품 행을 비관적 락으로 잡은 뒤 "대기/진행 중 경매가 있는지" 확인한다.
        // MySQL(REPEATABLE READ)은 트랜잭션의 첫 일반 조회 시점에 스냅샷을 고정하므로,
        // 방송을 먼저 조회하면 락을 기다리는 동안 다른 요청이 만든 경매를 보지 못해 같은 상품에 경매가 두 개 생긴다.
        Product product = productVerification(sellerId, request.productId());
        // 미발송 판매 취소가 반복된 회원은 경매를 등록할 수 없다 (위 '첫 조회' 규칙 때문에 상품을 잠근 뒤에 읽는다)
        sanctionService.requireNotBanned(sellerId, SanctionType.LISTING_BAN);
        if (requireVerified) {
            requireVerifiedSeller(sellerId);
        }
        // 방송 검증(존재, 방송 주인, 방송 중)
        LiveStream liveStream = ownLiveStream(sellerId, liveId);
        // 가격 규칙 (일반 경매와 같다)
        priceVerification(request.startPrice(), request.bidUnit(), request.buyNowPrice());

        //경매 종료 시간 계산
        LocalDateTime now=LocalDateTime.now();
        LocalDateTime endAt=now.plusSeconds(request.durationSec());

        //경매 생성 및 AuctionStatus을 ONGOING 변환->저장
        Auction auction = new Auction(product, liveStream, request.startPrice(), request.bidUnit(), request.buyNowPrice(), now, endAt);

        auction.start();

        Auction save = auctionRepository.save(auction);
        //커밋 뒤 시청 화면 경매 목록 갱신 + 채팅 안내
        eventPublisher.publishEvent(LiveAuctionEvent.of(save, LiveAuctionEvent.Type.STARTED));

        return AuctionResponse.from(save, this::imageUrl);
    }

    // 방송에 올리기 : 이미 만든 시작 전·진행 중 경매를 방송 화면에 보여준다. 경매 일정·상태는 바꾸지 않는다.
    public AuctionResponse showOnLive(Long sellerId, Long liveId, Long auctionId) {
        LiveStream liveStream = ownLiveStream(sellerId, liveId);

        Auction auction = auctionRepository.findById(auctionId).orElseThrow(
                () -> new BusinessException(AuctionErrorCode.NOT_FOUND)
        );

        //본인 경매인지
        if (!auction.getProduct().getSeller().getId().equals(sellerId)) {
            throw new BusinessException(AuctionErrorCode.NOT_OWNER);
        }

        LiveStream current = auction.getLiveStream();
        //이미 이 방송에 올라가 있으면(그 뒤 끝났더라도) 그대로 돌려준다
        if (current != null && current.getId().equals(liveStream.getId())) {
            return AuctionResponse.from(auction, this::imageUrl);
        }

        //새로 올리거나 옮길 때만 : 시작 전이거나 진행 중(마감 시각 전)인지
        boolean open = auction.getStatus() == AuctionStatus.READY || auction.getStatus() == AuctionStatus.ONGOING;
        if (!open || !auction.getEndAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(AuctionErrorCode.NOT_SHOWABLE_STATUS);
        }
        //다른 방송이 아직 진행 중이면 옮기지 않는다(끝난 방송이면 이 방송으로 옮김)
        if (current != null && current.getStatus() == LiveStreamStatus.LIVE) {
            throw new BusinessException(AuctionErrorCode.ON_OTHER_LIVE);
        }

        auction.showOn(liveStream);
        flushWithVersionCheck(auction);
        eventPublisher.publishEvent(LiveAuctionEvent.of(auction, LiveAuctionEvent.Type.SHOWN));

        return AuctionResponse.from(auction, this::imageUrl);
    }

    // 이 방송에 올라간 경매(누구나) : 최신순, 끝난 경매도 포함
    @Transactional(readOnly = true)
    public List<AuctionResponse> liveAuctions(Long liveId) {
        if (!liveStreamRepository.existsById(liveId)) {
            throw new BusinessException(LiveStreamErrorCode.NOT_FOUND);
        }
        return auctionRepository.findTop50ByLiveStreamIdOrderByIdDesc(liveId).stream().map(auction -> AuctionResponse.from(auction, this::imageUrl)).toList();
    }

    // 내 시작 전·진행 중 경매 : 스튜디오에서 방송에 올릴 경매를 고를 때 쓴다
    @Transactional(readOnly = true)
    public List<AuctionResponse> myOpenAuctions(Long sellerId) {
        return auctionRepository.findTop50ByProductSellerIdAndStatusInOrderByIdDesc(sellerId, List.of(AuctionStatus.READY, AuctionStatus.ONGOING))
                .stream().map(auction -> AuctionResponse.from(auction, this::imageUrl)).toList();
    }

    //목록 조회 및 검색(조건이 없을시 상태가 대기,진행 중인 경매들 중 최신순으로 정렬)
    @Transactional(readOnly = true)
    public Page<AuctionListResponse> searchAuctions(AuctionSearchCondition condition, Pageable pageable){
        return auctionRepository.search(condition, pageable).map(item -> item.withThumbnailKeyResolved(this::imageUrl));
    }

    //인기 경매 : 대기, 진행 중인 경매를 인기 점수(조회수 + 입찰 횟수 * 5) 높은 순으로 조회(개수 1~50 제한)
    // 10초 동안 같은 결과를 다시 쓴다 (CacheConfig.POPULAR_AUCTIONS). 개수를 1~50으로 맞춘 값으로 저장해서, 결과가 같은 요청끼리 나눠 쓴다.
    @Cacheable(cacheNames = CacheConfig.POPULAR_AUCTIONS, key = "T(java.lang.Math).min(T(java.lang.Math).max(#size,1),50)")
    @Transactional(readOnly = true)
    public List<AuctionListResponse> popularAuctions(int size){
       int limit= Math.min(Math.max(size,1),50);
        return auctionRepository.findPopular(limit).stream().map(item -> item.withThumbnailKeyResolved(this::imageUrl)).toList();
    }

    //상품의 경매 이력(판매자 본인만). 이상품으로 열었던 경매 전부(유찰,취소 포함)
    @Transactional(readOnly = true)
    public List<AuctionResponse> productAuctionHistory(Long sellerId, Long productId) {
        //상품 조회(삭제된 상품인지)
        Product product = productRepository.findById(productId).filter(
                        found -> found.getStatus() != Status.DELETED)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.NOT_FOUND)
                );

        //본인 상품인지
        if (!product.getSeller().getId().equals(sellerId)){
            throw new BusinessException(ProductErrorCode.NOT_OWNER_AUCTION_HISTORY);
        }

        List<Auction> auctions = auctionRepository.findTop50ByProductIdOrderByIdDesc(productId);
        //낙찰됐다가 주문이 취소된 경매는 사유(낙찰취소, 제품 미발송)를 함께 보여준다
        // 관리자 확인 뒤 환불한 주문은 취소 사유가 없다(null). toMap은 값이 null이면 NPE를 던지므로 뺀다. (그 경매는 사유 없이 보인다)
        Map<Long, OrderCancelReason> cancelReasons = orderRepository
                .findByAuctionIdInAndStatus(auctions.stream().map(Auction::getId).toList(), OrderStatus.CANCELED)
                .stream()
                .filter(order -> order.getCancelReason() != null)
                .collect(Collectors.toMap(Order::getAuctionId, Order::getCancelReason));
        return auctions.stream()
                .map(auction -> AuctionResponse.from(auction, this::imageUrl).withOrderCancelReason(cancelReasons.get(auction.getId())))
                .toList();
    }

    // 동시 같은 경매 수정시 먼저 반영 요청을 성공후 나머지는 409 CONCURRENT_UPDATE 응답
    private void flushWithVersionCheck(Auction auction) {
        try {
            auctionRepository.saveAndFlush(auction);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(AuctionErrorCode.CONCURRENT_UPDATE, e);
        }
    }

    //경매 상품 올릴시 공통 검증: 존재 여부,본인 상품,경매 상태가 READY,ONGOING 이외 인지
    private Product productVerification(Long sellerId, Long productId) {
        //상품 조회(삭제된 상품 제외)
        Product product = productRepository.findWithLockByIdAndStatusNot(productId, Status.DELETED).orElseThrow(
                () -> new BusinessException(ProductErrorCode.NOT_FOUND)
        );

        //본인 상품인지
        if (!product.getSeller().getId().equals(sellerId)) {
            throw new BusinessException(ProductErrorCode.NOT_OWNER);
        }

        //판매중인 상품인지(SOLD)
        if (product.getStatus() != Status.ACTIVE) {
            throw new BusinessException(AuctionErrorCode.PRODUCT_NOT_AVAILABLE);
        }

        //경매하려는 상품의 상태가 대기 OR 진행 중 해당되는지
        if (auctionRepository.existsByProductIdAndStatusIn(product.getId(), List.of(AuctionStatus.READY, AuctionStatus.ONGOING))) {
            throw new BusinessException(AuctionErrorCode.ALREADY_IN_AUCTION);
        }

        return product;
    }

    //방송 검증 : 존재 여부, 방송 주인, 방송 중(LIVE)
    private LiveStream ownLiveStream(Long sellerId, Long liveId) {
        LiveStream liveStream = liveStreamRepository.findById(liveId).orElseThrow(
                () -> new BusinessException(LiveStreamErrorCode.NOT_FOUND)
        );

        if (!liveStream.getSellerId().equals(sellerId)){
            throw new BusinessException(LiveStreamErrorCode.NOT_OWNER);
        }

        if (liveStream.getStatus() != LiveStreamStatus.LIVE){
            throw new BusinessException(LiveStreamErrorCode.NOT_LIVE);
        }
        return liveStream;
    }

    /**
     * 가격 검증.
     * - 시작가·최소 입찰 단위는 둘 다 입력하거나 둘 다 비운다. 하나만 비우면 INVALID_PRICE_SETTING
     * - 둘 다 비우면 입찰 없이 즉시구매로만 파는 경매라 즉시구매가가 필요하다. 셋 다 비우면 BUY_NOW_PRICE_REQUIRED
     * - 입찰을 받는 경매의 즉시구매가는 시작가보다 커야 한다
     */
    static void priceVerification(Long startPrice, Long bidUnit, Long buyNowPrice) {
        if (exceedsMaxPrice(startPrice) || exceedsMaxPrice(bidUnit) || exceedsMaxPrice(buyNowPrice)) {
            throw new BusinessException(AuctionErrorCode.PRICE_TOO_HIGH);
        }
        if ((startPrice == null) != (bidUnit == null)) {
            throw new BusinessException(AuctionErrorCode.INVALID_PRICE_SETTING);
        }
        if (startPrice == null) {
            if (buyNowPrice == null) {
                throw new BusinessException(AuctionErrorCode.BUY_NOW_PRICE_REQUIRED);
            }
            return;
        }
        if (buyNowPrice != null && buyNowPrice <= startPrice) {
            throw new BusinessException(AuctionErrorCode.INVALID_BUY_NOW_PRICE);
        }
    }

    private static boolean exceedsMaxPrice(Long price) {
        return price != null && price > Auction.MAX_PRICE;
    }

    /**
     * 일반 경매 시각 검증: 마감 > 시작, 마감은 지금 이후, 진행 시간 1분~14일, 시작은 30일 이내.
     * (마감이 지난 시각으로 만들면 스케줄러가 바로 유찰시킨다)
     * 상한이 없으면 먼 미래로 마감을 잡아 상품을 경매 중으로 계속 묶어 둘 수 있다. (경매 중인 상품은 수정·삭제 불가)
     */
    static void scheduleVerification(LocalDateTime startAt, LocalDateTime endAt, LocalDateTime now) {
        if (!startAt.isBefore(endAt) || !endAt.isAfter(now)) {
            throw new BusinessException(AuctionErrorCode.INVALID_TIME);
        }
        Duration duration = Duration.between(startAt, endAt);
        if (duration.compareTo(Auction.MIN_DURATION) < 0 || duration.compareTo(Auction.MAX_DURATION) > 0) {
            throw new BusinessException(AuctionErrorCode.INVALID_DURATION);
        }
        if (startAt.isAfter(now.plus(Auction.MAX_START_AHEAD))) {
            throw new BusinessException(AuctionErrorCode.START_TOO_FAR);
        }
    }

    private void requireVerifiedSeller(Long sellerId) {
        if (!sellerVerification.isVerified(sellerId)) {
            throw new BusinessException(AuctionErrorCode.NOT_VERIFIED_SELLER);
        }
    }

    // 상품 대표 이미지 키 → 화면에서 보여 줄 주소
    private String imageUrl(String key) {
        return fileStorage.publicUrl(key);
    }
}
