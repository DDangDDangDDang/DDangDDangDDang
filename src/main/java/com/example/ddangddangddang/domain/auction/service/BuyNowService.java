package com.example.ddangddangddang.domain.auction.service;

import com.example.ddangddangddang.domain.auction.dto.response.BuyNowResponse;
import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.event.AuctionEndedEvent;
import com.example.ddangddangddang.domain.auction.event.LiveAuctionEvent;
import com.example.ddangddangddang.domain.auction.exception.AuctionErrorCode;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.point.service.PointService;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 즉시구매. 드물고 로직이 길어서 경매 행에 비관적 락을 잡고 처리한다.
 * 락을 잡은 동안 들어온 입찰은 경매 행 UPDATE에서 기다렸다가, 커밋 후 status가 ENDED라 0행으로 실패한다.
 */
@Service
@RequiredArgsConstructor
public class BuyNowService {

    private final AuctionRepository auctionRepository;
    private final PointService pointService;
    private final AuctionSettlementService auctionSettlementService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public BuyNowResponse buyNow(Long buyerId, Long auctionId) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new BusinessException(AuctionErrorCode.NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        auction.validateBuyNow(buyerId, now);

        long price = auction.getBuyNowPrice();
        Long prevBidderId = auction.getHighestBidderId();

        if (prevBidderId == null) {
            pointService.purchase(buyerId, price, auctionId);
        } else {
            // 기존 최고 입찰자 홀딩 해제 + 구매자 차감을 userId 순서로 잠가 처리한다 (데드락 방지)
            pointService.releaseAndPurchase(prevBidderId, auction.getCurrentPrice(), buyerId, price, auctionId);
        }

        auction.buyNow(buyerId, now);
        Long orderId = auctionSettlementService.completeSale(auction);
        eventPublisher.publishEvent(AuctionEndedEvent.from(auction));
        if (auction.getLiveStream() != null) {
            eventPublisher.publishEvent(LiveAuctionEvent.of(auction, LiveAuctionEvent.Type.CHANGED));
        }

        return new BuyNowResponse(auctionId, orderId, price);
    }
}
