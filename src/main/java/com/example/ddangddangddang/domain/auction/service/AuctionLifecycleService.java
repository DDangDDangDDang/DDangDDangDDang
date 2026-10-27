package com.example.ddangddangddang.domain.auction.service;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.event.AuctionEndedEvent;
import com.example.ddangddangddang.domain.auction.event.AuctionStartedEvent;
import com.example.ddangddangddang.domain.auction.event.LiveAuctionEvent;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuctionLifecycleService {

    private final AuctionRepository auctionRepository;
    private final ApplicationEventPublisher eventPublisher;


    //경매 시작(READY -> ONGOING)
    @Transactional
    public void startAuction(Long auctionId){
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        //스케줄러가 목록을 찾은뒤 상태가 취소 혹은 진행 중일수 있기에 재검증
        //READY 상태가 아니면 시작하지 않음
        if (auction == null || auction.getStatus() != AuctionStatus.READY){
            return;
        }
        auction.start();
        eventPublisher.publishEvent(new AuctionStartedEvent(auction.getId()));
        //방송 중인 방송에 올라간 경매면 시청 화면에 시작을 알린다
        LiveStream liveStream = auction.getLiveStream();
        if (liveStream != null && liveStream.getStatus() == LiveStreamStatus.LIVE) {
            eventPublisher.publishEvent(LiveAuctionEvent.of(auction, LiveAuctionEvent.Type.STARTED));
        }
    }

    /**
     * 경매 마감 (ONGOING-> ENDED/FAILED) + 종료 이벤트 발생. 낙찰이면 같은 트랜잭션에서 정산한다 (AuctionSettlementListener)
     *
     * 경매 행을 먼저 잠근다. 정산이 낙찰자 지갑을 잠그므로 잠그는 순서가 "경매 → 지갑"이 되어 입찰·즉시구매와 같아진다.
     * 잠그지 않으면 "지갑 → (커밋 때) 경매" 순서가 되어, 마감 순간 들어온 입찰(경매 → 지갑)과 서로 기다리는 데드락이 날 수 있다.
     */
    @Transactional
    public void closeAuction(Long auctionId){
        Auction auction = auctionRepository.findByIdForUpdate(auctionId).orElse(null);
        //스케줄러가 목록을 찾은뒤 상태가 다시 바뀌었을수 있으니 재검증
        //ONGOING상태가 아니면 마감되지 않음
        if (auction == null || auction.getStatus() != AuctionStatus.ONGOING){
            return;
        }
        //그 사이 마감 직전 입찰로 연장됐으면 마감하지 않기
        if (auction.getEndAt().isAfter(LocalDateTime.now())){
            return;
        }
        auction.close();
        eventPublisher.publishEvent(AuctionEndedEvent.from(auction));
        if (auction.getLiveStream() != null) {
            eventPublisher.publishEvent(LiveAuctionEvent.closed(auction));
        }
    }
}
