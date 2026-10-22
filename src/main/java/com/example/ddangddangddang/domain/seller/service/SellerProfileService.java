package com.example.ddangddangddang.domain.seller.service;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.livestream.dto.SellerBroadcastSummary;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.entity.OrderStatus;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import com.example.ddangddangddang.domain.seller.dto.SellerAuctionResponse;
import com.example.ddangddangddang.domain.seller.dto.SellerProfileResponse;
import com.example.ddangddangddang.domain.seller.entity.SellerApplication;
import com.example.ddangddangddang.domain.seller.repository.SellerApplicationRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 판매자 프로필: 기본 정보(인증·구독자·방송 기록·신뢰 지표)와 경매 기록.
 * 방송·경매·주문 도메인의 값을 읽기만 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerProfileService {

    static final int MAX_AUCTION_PAGE_SIZE = 30;

    private final UserRepository userRepository;
    private final SellerApplicationRepository applicationRepository;
    private final SubscriptionService subscriptionService;
    private final LiveStreamRepository liveStreamRepository;
    private final AuctionRepository auctionRepository;
    private final OrderRepository orderRepository;

    /** @param viewerId 보는 사람 (로그인하지 않았으면 null) */
    public SellerProfileResponse getProfile(Long sellerId, Long viewerId) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        SellerApplication approved = applicationRepository.findByUserId(sellerId)
                .filter(SellerApplication::isApproved).orElse(null);

        // 방송 기록은 집계 쿼리로 구한다. (방송이 많아도 엔티티를 모두 읽지 않음) 방송 중인 방송은 판매자당 하나라 따로 읽는다.
        SellerBroadcastSummary summary = liveStreamRepository.summarizeBroadcasts(sellerId);
        LiveStream live = liveStreamRepository.findByActiveSellerId(sellerId)
                .filter(s -> s.getStatus() == LiveStreamStatus.LIVE).orElse(null);
        Long endedSeconds = liveStreamRepository.sumEndedBroadcastSeconds(sellerId);

        return new SellerProfileResponse(
                sellerId,
                seller.getNickname(),
                approved != null,
                approved == null ? null : approved.getStoreName(),
                approved == null ? null : approved.getIntroduction(),
                approved == null ? null : approved.getReviewedAt(),
                seller.getCreatedAt(),
                subscriptionService.countSubscribers(sellerId),
                subscriptionService.isSubscribed(viewerId, sellerId),
                live == null ? null : live.getId(),
                summary == null ? null : summary.firstStartedAt(),
                summary == null || summary.broadcastCount() == null ? 0 : summary.broadcastCount(),
                totalBroadcastSeconds(endedSeconds, live, LocalDateTime.now()),
                new SellerProfileResponse.Trust(
                        seller.getPhoneNumber() != null,
                        auctionRepository.countByProductSellerIdAndStatus(sellerId, AuctionStatus.ENDED),
                        auctionRepository.countByProductSellerIdAndStatus(sellerId, AuctionStatus.FAILED),
                        auctionRepository.countByProductSellerIdAndStatus(sellerId, AuctionStatus.CANCELED),
                        orderRepository.countBySellerIdAndStatus(sellerId, OrderStatus.COMPLETED),
                        averageShippingHours(orderRepository.findTop100BySellerIdAndShippedAtIsNotNullOrderByIdDesc(sellerId))
                )
        );
    }

    /** 경매 기록. 최근 경매부터 size개씩 (취소한 경매도 포함) */
    public CursorResponse<SellerAuctionResponse> getAuctions(Long sellerId, Long cursor, int size) {
        if (!userRepository.existsById(sellerId)) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        int pageSize = Math.max(1, Math.min(size, MAX_AUCTION_PAGE_SIZE));
        List<Auction> fetched = auctionRepository.findSellerHistory(sellerId, cursor, PageRequest.of(0, pageSize + 1));
        return CursorResponse.of(fetched, pageSize, SellerAuctionResponse::from, Auction::getId);
    }

    /** 방송한 시간 합(초) = 끝난 방송의 시작~종료 합 + 방송 중이면 시작~지금 */
    static long totalBroadcastSeconds(Long endedSeconds, LiveStream live, LocalDateTime now) {
        long ended = endedSeconds == null ? 0 : Math.max(0, endedSeconds);
        if (live == null || live.getStartedAt() == null) {
            return ended;
        }
        return ended + Math.max(0, Duration.between(live.getStartedAt(), now).toSeconds());
    }

    /** 주문부터 발송까지 걸린 평균 시간(시간 단위, 소수 첫째 자리). 발송한 주문이 없으면 null */
    static Double averageShippingHours(List<Order> shipped) {
        if (shipped.isEmpty()) {
            return null;
        }
        double averageMinutes = shipped.stream()
                .mapToLong(o -> Math.max(0, Duration.between(o.getCreatedAt(), o.getShippedAt()).toMinutes()))
                .average().orElse(0);
        return Math.round(averageMinutes / 60.0 * 10) / 10.0;
    }
}
