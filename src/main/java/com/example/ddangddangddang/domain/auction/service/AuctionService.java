package com.example.ddangddangddang.domain.auction.service;

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
import com.example.ddangddangddang.domain.livechat.exception.LiveChatErrorCode;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.channels.spi.AbstractSelectableChannel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.IllegalFormatCodePointException;
import java.util.List;

@RequiredArgsConstructor
@Service
@Transactional
public class AuctionService {
    private final AuctionRepository auctionRepository;
    private final ProductRepository productRepository;
    private final LiveStreamRepository liveStreamRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final FileStorage fileStorage;

    //경매(일반) 생성 : 본인 상품이고 판매 중이며 대기 or 진행 중인 경매가 아닐때 생성이 가능합니다
    public AuctionResponse auctionCreate(Long sellerId, AuctionCreateRequest request) {
        //삼품 검증(공통)
        Product product=productVerification(sellerId,request.productId());

        //시작 시간< 종료 시간
        if (!request.startAt().isBefore(request.endAt())) {
            throw new BusinessException(AuctionErrorCode.INVALID_TIME);
        }

        //즉시구매가>시작가격
        immediatePurchaseVerification(request.buyNowPrice(),request.startPrice());
        //저장(일반 경매라 라이브 null)
        Auction save = auctionRepository.save(new Auction(product, null, request.startPrice(), request.bidUnit(), request.buyNowPrice(), request.startAt(), request.endAt()));

        return AuctionResponse.from(save, this::imageUrl);

    }

    //경매 상세 조회
    @Transactional(readOnly = true)
    public AuctionResponse findAddBy(Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId).orElseThrow(
                () -> new BusinessException(AuctionErrorCode.NOT_FOUND)
        );

        return AuctionResponse.from(auction, this::imageUrl);
    }

    //경매 취소(본인 경매이며 대기OR진행 중이며 입찰이 0건일시에만 취소가 가능합니다)
    public void cancelAuction(Long sellerId, Long auctionId) {

        //경매가 존재 하는지
        Auction auction = auctionRepository.findById(auctionId).orElseThrow(
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

        // 상품 검증(공통) - 반드시 이 트랜잭션의 첫 조회여야 한다 (동시성)
        // productVerification은 상품 행을 비관적 락으로 잡은 뒤 "대기/진행 중 경매가 있는지" 확인한다.
        // MySQL(REPEATABLE READ)은 트랜잭션의 첫 일반 조회 시점에 스냅샷을 고정하므로,
        // 방송을 먼저 조회하면 락을 기다리는 동안 다른 요청이 만든 경매를 보지 못해 같은 상품에 경매가 두 개 생긴다.
        Product product = productVerification(sellerId, request.productId());
        // 방송 검증(존재, 방송 주인, 방송 중)
        LiveStream liveStream = ownLiveStream(sellerId, liveId);
        // 즉시 구매가
        immediatePurchaseVerification(request.buyNowPrice(),request.startPrice());

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

    //인기 경매 : 진행 중인 경매 중 입찰 횟수 많은 순 조회(개수 1~50 제한)
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

        return auctionRepository.findTop50ByProductIdOrderByIdDesc(productId)
                .stream().map(auction -> AuctionResponse.from(auction, this::imageUrl)).toList();
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

    //즉시 구매가 검증(즉시구매가>시작가격)
    private void immediatePurchaseVerification(Long buyNowPrice, Long startPrice) {
        if (buyNowPrice != null && buyNowPrice <= startPrice) {
            throw new BusinessException(AuctionErrorCode.INVALID_BUY_NOW_PRICE);
        }
    }

    // 상품 대표 이미지 키 → 화면에서 보여 줄 주소
    private String imageUrl(String key) {
        return fileStorage.publicUrl(key);
    }
}
