package com.example.ddangddangddang.domain.auction.service;

import com.example.ddangddangddang.domain.auction.dto.response.BidResponse;
import com.example.ddangddangddang.domain.auction.exception.AuctionErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 입찰의 트랜잭션 범위를 정하는 곳. 이 클래스에는 @Transactional을 붙이지 않는다.
 *
 * 다른 입찰이 먼저 반영돼 조건부 UPDATE가 실패하면, 새 트랜잭션으로 다시 조회해서 재시도한다.
 * 다시 조회했을 때 내 금액이 더는 유효하지 않으면(누군가 더 높게 입찰) 검증에서 BID_TOO_LOW로 끝난다.
 * 즉 재시도는 "가격이 바뀌었지만 내 입찰이 여전히 이길 수 있는 경우"에만 성공한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidFacade {

    static final int MAX_ATTEMPTS = 5;
    /** 재시도 전 기다리는 시간(밀리초). 동시에 실패한 입찰들이 같은 순간 다시 부딪히지 않게 무작위로 흩는다 */
    private static final int MIN_BACKOFF_MS = 10;
    private static final int MAX_BACKOFF_MS = 50;

    private final BidService bidService;

    public BidResponse placeBid(Long bidderId, Long auctionId, long amount) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                Optional<BidResponse> result = bidService.tryPlaceBid(bidderId, auctionId, amount);
                if (result.isPresent()) {
                    return result.get();
                }
            } catch (PessimisticLockingFailureException e) {
                // 락 대기 시간 초과나 데드락 감지로 롤백된 경우. 락 순서를 지키므로 드물지만 재시도한다
                log.warn("입찰 락 충돌, 재시도 auctionId={}, attempt={}", auctionId, attempt, e);
            }
            if (attempt < MAX_ATTEMPTS) {
                backoff();
            }
        }
        throw new BusinessException(AuctionErrorCode.BID_CONFLICT);
    }

    private void backoff() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(MIN_BACKOFF_MS, MAX_BACKOFF_MS + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(AuctionErrorCode.BID_CONFLICT);
        }
    }
}
