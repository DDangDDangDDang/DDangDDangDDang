package com.example.ddangddangddang.domain.donation.repository;

import com.example.ddangddangddang.domain.donation.entity.DonationPointLot;
import com.example.ddangddangddang.domain.order.entity.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface DonationPointLotRepository extends JpaRepository<DonationPointLot, Long> {

    boolean existsByOrderId(Long orderId);

    /**
     * 적립 보정: from ~ to 사이에 구매 확정했는데 적립 묶음이 없는 주문. (적립 이벤트 처리가 실패한 주문) afterId보다 큰 주문을 ID 순으로
     * 주문 도메인을 읽기만 한다.
     */
    @Query("""
            select o from Order o
            where o.status = com.example.ddangddangddang.domain.order.entity.OrderStatus.COMPLETED
              and o.completedAt >= :from and o.completedAt < :to
              and o.id > :afterId
              and not exists (select 1 from DonationPointLot l where l.orderId = o.id)
            order by o.id asc
            """)
    List<Order> findConfirmedWithoutLot(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                        @Param("afterId") Long afterId, Pageable pageable);

    /** from ~ to(제외) 사이에 적립한 합계 (한 달 적립 한도) */
    @Query("""
            select coalesce(sum(l.amount), 0) from DonationPointLot l
            where l.userId = :userId and l.earnedAt >= :from and l.earnedAt < :to
            """)
    long sumEarnedBetween(@Param("userId") Long userId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * 이 판매자에게 선물할 때 쓸 수 있는 묶음: 남아 있고, 유효기간이 지나지 않았고, 이 판매자와의 거래로 받은 것이 아닌 것.
     * 유효기간이 먼저 끝나는 것부터 쓴다.
     */
    @Query("""
            select l from DonationPointLot l
            where l.userId = :userId and l.remaining > 0 and l.expiresAt > :now and l.sellerId <> :sellerId
            order by l.expiresAt asc, l.id asc
            """)
    List<DonationPointLot> findUsableFor(@Param("userId") Long userId, @Param("sellerId") Long sellerId, @Param("now") LocalDateTime now);

    /** 이 판매자에게 선물할 수 있는 포인트 합계 */
    @Query("""
            select coalesce(sum(l.remaining), 0) from DonationPointLot l
            where l.userId = :userId and l.remaining > 0 and l.expiresAt > :now and l.sellerId <> :sellerId
            """)
    long sumUsableFor(@Param("userId") Long userId, @Param("sellerId") Long sellerId, @Param("now") LocalDateTime now);

    /** now ~ until 사이에 유효기간이 끝나는 남은 포인트 합계 (곧 사라질 포인트) */
    @Query("""
            select coalesce(sum(l.remaining), 0) from DonationPointLot l
            where l.userId = :userId and l.remaining > 0 and l.expiresAt > :now and l.expiresAt <= :until
            """)
    long sumExpiringBetween(@Param("userId") Long userId, @Param("now") LocalDateTime now, @Param("until") LocalDateTime until);

    /** 가장 먼저 끝나는 남은 묶음의 유효기간 (없으면 null) */
    @Query("""
            select min(l.expiresAt) from DonationPointLot l
            where l.userId = :userId and l.remaining > 0 and l.expiresAt > :now
            """)
    LocalDateTime findNextExpiry(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** 유효기간이 지났는데 남은 양이 있는 묶음의 회원. afterUserId보다 큰 회원을 userId 순으로 (처음이면 0) */
    @Query("""
            select distinct l.userId from DonationPointLot l
            where l.expiresAt <= :now and l.remaining > 0 and l.userId > :afterUserId
            order by l.userId asc
            """)
    List<Long> findUserIdsWithExpired(@Param("now") LocalDateTime now, @Param("afterUserId") Long afterUserId, Pageable pageable);

    @Query("""
            select l from DonationPointLot l
            where l.userId = :userId and l.expiresAt <= :now and l.remaining > 0
            """)
    List<DonationPointLot> findExpired(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** 곧 사라진다고 알린 묶음으로 표시한다. (알림을 저장한 뒤에) */
    @Modifying(clearAutomatically = true)
    @Query("update DonationPointLot l set l.expiryNotified = true where l.id in :ids")
    int markExpiryNotified(@Param("ids") Collection<Long> ids);

    /** 곧 사라질 묶음 중 아직 알리지 않은 것이 있는 회원. afterUserId보다 큰 회원을 userId 순으로 */
    @Query("""
            select distinct l.userId from DonationPointLot l
            where l.remaining > 0 and l.expiresAt > :now and l.expiresAt <= :until and l.expiryNotified = false
              and l.userId > :afterUserId
            order by l.userId asc
            """)
    List<Long> findUserIdsToNotify(@Param("now") LocalDateTime now, @Param("until") LocalDateTime until,
                                   @Param("afterUserId") Long afterUserId, Pageable pageable);

    /** 이 회원들의 곧 사라질 묶음 중 아직 알리지 않은 것 전부 (회원별로 한 번에 합쳐 알린다) */
    @Query("""
            select l from DonationPointLot l
            where l.userId in :userIds and l.remaining > 0 and l.expiresAt > :now and l.expiresAt <= :until and l.expiryNotified = false
            """)
    List<DonationPointLot> findToNotify(@Param("userIds") Collection<Long> userIds, @Param("now") LocalDateTime now,
                                        @Param("until") LocalDateTime until);
}
