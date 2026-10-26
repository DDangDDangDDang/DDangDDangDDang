package com.example.ddangddangddang.domain.auction.service;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 마감(정산 포함)에 실패한 경매의 재시도 시각을 늦춘다.
 *
 * 마감이 실패하면 그 트랜잭션은 롤백되므로, 실패 기록은 새 트랜잭션(REQUIRES_NEW)에서 남긴다.
 * 일시적인 실패(락 대기 초과, DB 연결 끊김)는 다음 시도에 대부분 성공한다.
 * 한도까지 실패하면 데이터가 어긋난 것(예: 묶인 포인트가 낙찰가보다 적음)이라 자동으로 처리하지 않고 멈춘 뒤 에러 로그를 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionCloseRetryService {

    private final AuctionRepository auctionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long auctionId, Exception cause) {
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null) {
            return;
        }
        boolean exhausted = auction.recordCloseFailure(LocalDateTime.now());
        if (exhausted) {
            log.error("[경매 마감 중단] {}번 실패해 더 시도하지 않습니다. 수동 확인이 필요합니다. auctionId={}",
                    auction.getCloseFailCount(), auctionId, cause);
        } else {
            log.warn("경매 마감 실패, {}에 다시 시도 auctionId={}, failCount={}",
                    auction.getNextCloseAttemptAt(), auctionId, auction.getCloseFailCount(), cause);
        }
    }
}
