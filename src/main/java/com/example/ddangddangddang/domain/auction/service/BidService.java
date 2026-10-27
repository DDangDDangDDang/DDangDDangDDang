package com.example.ddangddangddang.domain.auction.service;

import com.example.ddangddangddang.domain.auction.dto.response.BidResponse;
import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.entity.Bid;
import com.example.ddangddangddang.domain.auction.entity.BidStatus;
import com.example.ddangddangddang.domain.auction.event.BidOutbidEvent;
import com.example.ddangddangddang.domain.auction.event.BidPlacedEvent;
import com.example.ddangddangddang.domain.auction.event.LiveAuctionEvent;
import com.example.ddangddangddang.domain.auction.exception.AuctionErrorCode;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.auction.repository.BidRepository;
import com.example.ddangddangddang.domain.point.service.PointService;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 입찰 한 번 시도 = 트랜잭션 하나. 재시도는 BidFacade가 맡는다.
 *
 * 왜 재시도를 이 트랜잭션 안에서 하지 않나:
 * MySQL 기본 격리 수준(REPEATABLE READ)에서는 같은 트랜잭션 안에서 다시 조회해도 처음 본 값이 그대로 보인다.
 * 그러면 다른 입찰로 바뀐 가격을 영영 못 보고 같은 실패를 반복한다. 그래서 시도마다 새 트랜잭션을 연다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final PointService pointService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 입찰을 한 번 시도한다.
     * 1. 경매 조회 + 입찰 규칙 검증 (실패하면 예외)
     * 2. 조건부 UPDATE: 조회한 version 그대로일 때만 반영
     * 3. 반영됐으면 포인트 홀딩 (이전 최고 입찰자 해제 + 새 입찰자 홀딩)
     *
     * 2와 3은 같은 트랜잭션이다. 포인트가 부족하면 예외로 경매 UPDATE까지 롤백된다.
     * 락 순서는 "경매 행(UPDATE가 커밋까지 잡음) → 지갑(replaceHold가 userId 순서로 잡음)"이다.
     *
     * @return 그 사이 다른 입찰이 먼저 반영돼서 실패하면 빈 값 (재시도 대상)
     */
    @Transactional
    public Optional<BidResponse> tryPlaceBid(Long bidderId, Long auctionId, long amount) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new BusinessException(AuctionErrorCode.NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        auction.validateBid(bidderId, amount, now);

        // UPDATE 후에는 영속성 컨텍스트가 비워지므로 필요한 값을 먼저 꺼내둔다
        Long prevBidderId = auction.getHighestBidderId();
        long prevAmount = auction.getCurrentPrice();
        int nextBidCount = auction.getBidCount() + 1;
        LocalDateTime newEndAt = auction.endAtAfterBid(now);
        boolean extended = newEndAt.isAfter(auction.getEndAt());
        Long liveStreamId = auction.getLiveStream() == null ? null : auction.getLiveStream().getId();
        String productName = auction.getProduct().getName();
        LocalDateTime startAt = auction.getStartAt();

        int updated = auctionRepository.placeBid(
                auctionId, bidderId, amount, newEndAt, extended ? 1 : 0,
                auction.getVersion(), AuctionStatus.ONGOING, now);
        if (updated == 0) {
            return Optional.empty();
        }

        if (prevBidderId == null) {
            pointService.hold(bidderId, amount, auctionId);
        } else {
            pointService.replaceHold(bidderId, amount, prevBidderId, prevAmount, auctionId);
        }

        // 밀려난 최고 입찰을 OUTBID로 바꾸고 새 입찰을 ACTIVE로 저장한다 (경매 행은 위 UPDATE로 잠겨 있다)
        bidRepository.changeStatus(auctionId, BidStatus.ACTIVE, BidStatus.OUTBID);
        Bid bid = bidRepository.save(Bid.create(auctionId, bidderId, amount));
        eventPublisher.publishEvent(new BidPlacedEvent(auctionId, bidderId, amount, nextBidCount, newEndAt, extended));
        // 최고 입찰자에서 밀려난 회원에게 알린다
        if (prevBidderId != null && !prevBidderId.equals(bidderId)) {
            eventPublisher.publishEvent(new BidOutbidEvent(auctionId, prevBidderId, amount));
        }
        // 방송에 올라간 경매면 시청 화면의 경매 목록을 갱신하고 입찰 안내를 띄운다 (현재가가 바뀌었으므로)
        if (liveStreamId != null) {
            eventPublisher.publishEvent(LiveAuctionEvent.bid(liveStreamId, auctionId, productName, amount, startAt, bidderId, prevAmount));
        }

        return Optional.of(new BidResponse(bid.getId(), auctionId, amount, nextBidCount, newEndAt, extended));
    }
}
