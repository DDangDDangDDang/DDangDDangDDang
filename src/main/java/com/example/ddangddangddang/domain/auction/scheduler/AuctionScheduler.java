package com.example.ddangddangddang.domain.auction.scheduler;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.auction.service.AuctionLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

//경매 시작 마감 자동 처리 (1초 마다 반복실행)
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionScheduler {
    private final AuctionRepository auctionRepository;
    private final AuctionLifecycleService auctionLifecycleService;

    //시작 시간이 된 대기 경매 시작
    @Scheduled(fixedDelay = 1000)
    public void startAuctions(){
        List<Auction> targets = auctionRepository.findByStatusAndStartAtLessThanEqual(AuctionStatus.READY, LocalDateTime.now());
        for (Auction auction: targets){
            try {
                auctionLifecycleService.startAuction(auction.getId());
            } catch (Exception e){
                //하나가 실패해도 나머지는 게속 진행
                log.warn("경매 시작 처리 실패 auctionId={}",auction.getId(), e);
            }
        }
    }

    //종료 시간이 지난 진행 중 경매 마감
    @Scheduled(fixedDelay = 1000)
    public void closeAuctions(){
        List<Auction> targets = auctionRepository.findByStatusAndEndAtLessThanEqual(AuctionStatus.ONGOING, LocalDateTime.now());

        for (Auction auction: targets){
            try {
                auctionLifecycleService.closeAuction(auction.getId());
            } catch (Exception e){
                //하나가 실패해도 나머지는 게속 진행
                log.warn("경매 마감 처리 실패 auctionId={}",auction.getId(), e);
            }
        }
    }


}
