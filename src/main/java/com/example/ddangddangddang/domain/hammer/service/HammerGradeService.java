package com.example.ddangddangddang.domain.hammer.service;

import com.example.ddangddangddang.domain.hammer.entity.HammerGrade;
import com.example.ddangddangddang.domain.hammer.repository.HammerScoreRepository;
import com.example.ddangddangddang.domain.hammer.service.HammerScore.Deal;
import com.example.ddangddangddang.domain.order.entity.OrderCancelReason;
import com.example.ddangddangddang.domain.order.entity.OrderStatus;
import com.example.ddangddangddang.global.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

/**
 * 회원의 망치 등급. 점수를 따로 저장하지 않고 최근 12개월의 주문·리뷰 기록으로 그때그때 계산한다.
 * (리뷰는 고치거나 지울 수 있고, 기록이 1년 지나면 저절로 빠지므로 저장해 둔 점수와 어긋날 일이 없다)
 * 판매자 프로필을 부를 때마다 계산하지 않도록 Redis에 10분 캐시한다. (CacheConfig.HAMMER_GRADE) Redis가 안 되면 매번 계산한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HammerGradeService {

    static final int WINDOW_DAYS = 365;

    private final HammerScoreRepository hammerScoreRepository;

    @Cacheable(cacheNames = CacheConfig.HAMMER_GRADE, key = "#userId")
    public HammerGrade gradeOf(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusDays(WINDOW_DAYS);

        List<Deal> deals = Stream.concat(
                hammerScoreRepository.findBuyerDeals(userId, OrderStatus.COMPLETED, since).stream()
                        .map(row -> Deal.asBuyer(row.partnerId(), row.completedAt())),
                hammerScoreRepository.findSellerDeals(userId, OrderStatus.COMPLETED, since).stream()
                        .map(row -> Deal.asSeller(row.partnerId(), row.completedAt(), row.reviewRating()))
        ).toList();
        long noAddressCancels = hammerScoreRepository.countByBuyerIdAndCancelReasonAndCanceledAtGreaterThanEqual(
                userId, OrderCancelReason.BUYER_NO_ADDRESS, since);
        long notShippedCancels = hammerScoreRepository.countBySellerIdAndCancelReasonAndCanceledAtGreaterThanEqual(
                userId, OrderCancelReason.SELLER_NOT_SHIPPED, since);
        long completedDealCount = hammerScoreRepository.countByBuyerIdAndStatus(userId, OrderStatus.COMPLETED)
                + hammerScoreRepository.countBySellerIdAndStatus(userId, OrderStatus.COMPLETED);

        return HammerScore.grade(HammerScore.score(deals, noAddressCancels, notShippedCancels), completedDealCount);
    }
}
