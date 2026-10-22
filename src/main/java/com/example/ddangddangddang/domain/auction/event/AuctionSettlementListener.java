package com.example.ddangddangddang.domain.auction.event;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.exception.AuctionErrorCode;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.auction.service.AuctionSettlementService;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 스케줄러가 경매를 낙찰(ENDED)로 마감하면, 낙찰자 포인트 차감 + 상품 판매 완료 + 주문 생성을 한다.
 *
 * BEFORE_COMMIT이라 마감과 같은 트랜잭션에서 실행된다.
 * 정산이 실패하면 마감도 함께 롤백되고, 스케줄러가 다음 주기(1초 뒤)에 다시 마감을 시도한다.
 * 그래서 "경매는 끝났는데 포인트가 안 빠진" 상태가 남지 않는다.
 *
 * 마감 코드(AuctionLifecycleService)를 고치지 않고 정산을 붙이려고 이벤트로 받는다.
 * 마감은 입찰과 겹치지 않는다: 입찰은 종료 시각 전에만, 마감은 종료 시각이 지난 뒤에만 성공하기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class AuctionSettlementListener {

    private final AuctionRepository auctionRepository;
    private final OrderRepository orderRepository;
    private final AuctionSettlementService auctionSettlementService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void settleWinningBid(AuctionEndedEvent event) {
        if (event.result() != AuctionStatus.ENDED) {
            return; // 유찰(FAILED): 홀딩된 포인트가 없으므로 정산할 것이 없다
        }
        if (orderRepository.existsByAuctionId(event.auctionId())) {
            return; // 즉시구매처럼 이미 정산된 경매 (같은 트랜잭션에서 주문까지 만든 뒤 이벤트를 발행한다)
        }

        Auction auction = auctionRepository.findById(event.auctionId())
                .orElseThrow(() -> new BusinessException(AuctionErrorCode.NOT_FOUND));
        auctionSettlementService.settleWinningBid(auction);
    }
}
