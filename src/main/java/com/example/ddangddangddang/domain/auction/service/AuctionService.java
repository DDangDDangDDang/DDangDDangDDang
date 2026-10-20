package com.example.ddangddangddang.domain.auction.service;

import com.example.ddangddangddang.domain.auction.dto.request.AuctionCreateRequest;
import com.example.ddangddangddang.domain.auction.dto.request.LiveAuctionCreateRequest;
import com.example.ddangddangddang.domain.auction.dto.response.AuctionResponse;
import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        return AuctionResponse.from(save);

    }

    //경매 상세 조회
    @Transactional(readOnly = true)
    public AuctionResponse findAddBy(Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId).orElseThrow(
                () -> new BusinessException(AuctionErrorCode.NOT_FOUND)
        );

        return AuctionResponse.from(auction);
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

    }

    // 라이브 중 경매 생성 : 방송 주인이 라이브 중 경매 생성,생성 즉시 AuctionStatus는 ONGOING변환
    public AuctionResponse createLiveAuction (Long sellerId, Long liveId,LiveAuctionCreateRequest request){

        // 방송 존재 여부
        LiveStream liveStream = liveStreamRepository.findById(liveId).orElseThrow(
                () -> new BusinessException(LiveStreamErrorCode.NOT_FOUND)
        );

        //방송 주인인지
        if (!liveStream.getSellerId().equals(sellerId)){
            throw new BusinessException(LiveStreamErrorCode.NOT_OWNER);
        }

        // LiveStreamStatus==LIVE 인지
        if (liveStream.getStatus() != LiveStreamStatus.LIVE){
            throw new BusinessException(LiveStreamErrorCode.NOT_LIVE);
        }
        // 상품 검증(공통)
        Product product = productVerification(sellerId, request.productId());
        // 즉시 구매가
        immediatePurchaseVerification(request.buyNowPrice(),request.startPrice());

        //경매 종료 시간 계산
        LocalDateTime now=LocalDateTime.now();
        LocalDateTime endAt=now.plusSeconds(request.durationSec());

        //경매 생성 및 AuctionStatus을 ONGOING 변환->저장
        Auction auction = new Auction(product, liveStream, request.startPrice(), request.bidUnit(), request.buyNowPrice(), now, endAt);

        auction.start();

        Auction save = auctionRepository.save(auction);

        return AuctionResponse.from(save);
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

    //즉시 구매가 검증(즉시구매가>시작가격)
    private void immediatePurchaseVerification(Long buyNowPrice, Long startPrice) {
        if (buyNowPrice != null && buyNowPrice <= startPrice) {
            throw new BusinessException(AuctionErrorCode.INVALID_BUY_NOW_PRICE);
        }
    }
}
