package com.example.ddangddangddang.domain.auction.scheduler;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.auction.repository.AuctionViewRepository;
import com.example.ddangddangddang.domain.auction.service.AuctionCloseRetryService;
import com.example.ddangddangddang.domain.auction.service.AuctionLifecycleService;
import com.example.ddangddangddang.domain.auction.service.AuctionViewCountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// 경매 자동 처리: 시작,마감은 1초마다, 조회수 옮겨 적기는 1분마다.
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionScheduler {
    private final AuctionRepository auctionRepository;
    private final AuctionLifecycleService auctionLifecycleService;
    private final AuctionCloseRetryService auctionCloseRetryService;
    private final AuctionViewRepository auctionViewRepository;
    private final AuctionViewCountService auctionViewCountService;

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

    //종료 시간이 지난 진행 중 경매 마감. 실패한 경매는 기다렸다가 다시 시도하고, 한도에 닿으면 멈춘다
    @Scheduled(fixedDelay = 1000)
    public void closeAuctions(){
        List<Auction> targets = auctionRepository.findCloseTargets(
                AuctionStatus.ONGOING, LocalDateTime.now(), Auction.MAX_CLOSE_FAILURES);

        for (Auction auction: targets){
            try {
                auctionLifecycleService.closeAuction(auction.getId());
            } catch (Exception e){
                //하나가 실패해도 나머지는 게속 진행. 다음 시도 시각을 늦춘다
                recordCloseFailure(auction.getId(), e);
            }
        }
    }

    private void recordCloseFailure(Long auctionId, Exception cause) {
        try {
            auctionCloseRetryService.recordFailure(auctionId, cause);
        } catch (Exception e) {
            // 기록까지 실패하면(DB 장애 등) 다음 주기에 그대로 다시 시도한다
            log.warn("경매 마감 처리 실패 auctionId={}", auctionId, cause);
        }
    }

    // 1분 마다 Redis에 모인 조회수를 DB에 더하고, 더한 만큼 Redis에서 뺀다
    @Scheduled(fixedDelay = 60_000)
    public void syncViewCounts(){
        try {
            Map<Long, Long> viewCounts = auctionViewRepository.allViewCounts();
            if (viewCounts.isEmpty()){
                return;
            }
            // DB에 다 더한 다음에 Redis에서 뺀다 (먼저 빼면, DB 저장이 실패했을 때 조회수가 사라지기 때문이다)
            auctionViewCountService.addViewCounts(viewCounts);
            auctionViewRepository.subtract(viewCounts);
        } catch (Exception e){
            // DB 저장이 실패하면 Redis에 그대로 남아 1분 뒤에 다시 더한다.
            // DB에 더한 뒤 Redis 빼기만 실패하면 다음에 한 번 더 더해진다 (조회수가 조금 늘 뿐이라 허용)
            log.warn("경매 조회수 옮겨 적기 실패", e);
        }
    }


}
