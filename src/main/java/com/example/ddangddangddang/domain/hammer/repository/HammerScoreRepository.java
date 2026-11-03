package com.example.ddangddangddang.domain.hammer.repository;

import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.entity.OrderCancelReason;
import com.example.ddangddangddang.domain.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 망치 점수에 쓰는 주문·리뷰 기록을 읽기만 한다. (주문·리뷰 도메인은 망치 점수를 모른다)
 */
public interface HammerScoreRepository extends Repository<Order, Long> {

    /** 구매 확정 거래 하나: 거래 상대, 확정 시각, 판매자로 한 거래면 받은 리뷰 점수 (없으면 null) */
    record DealRow(Long partnerId, LocalDateTime completedAt, Integer reviewRating) {
        public DealRow(Long partnerId, LocalDateTime completedAt) {
            this(partnerId, completedAt, null);
        }
    }

    @Query("""
            select new com.example.ddangddangddang.domain.hammer.repository.HammerScoreRepository$DealRow(o.sellerId, o.completedAt)
            from Order o
            where o.buyerId = :userId and o.status = :status and o.completedAt >= :since""")
    List<DealRow> findBuyerDeals(@Param("userId") Long userId, @Param("status") OrderStatus status,
                                 @Param("since") LocalDateTime since);

    @Query("""
            select new com.example.ddangddangddang.domain.hammer.repository.HammerScoreRepository$DealRow(o.buyerId, o.completedAt, r.rating)
            from Order o left join Review r on r.orderId = o.id
            where o.sellerId = :userId and o.status = :status and o.completedAt >= :since""")
    List<DealRow> findSellerDeals(@Param("userId") Long userId, @Param("status") OrderStatus status,
                                  @Param("since") LocalDateTime since);

    long countByBuyerIdAndCancelReasonAndCanceledAtGreaterThanEqual(Long buyerId, OrderCancelReason reason, LocalDateTime since);

    long countBySellerIdAndCancelReasonAndCanceledAtGreaterThanEqual(Long sellerId, OrderCancelReason reason, LocalDateTime since);

    long countByBuyerIdAndStatus(Long buyerId, OrderStatus status);

    long countBySellerIdAndStatus(Long sellerId, OrderStatus status);
}
