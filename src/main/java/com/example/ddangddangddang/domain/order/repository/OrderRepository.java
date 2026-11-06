package com.example.ddangddangddang.domain.order.repository;

import com.example.ddangddangddang.domain.order.entity.Courier;
import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByAuctionId(Long auctionId);

    /** 판매자 경매 이력: 경매들의 주문 중 그 상태인 것 (취소 사유 표시) */
    List<Order> findByAuctionIdInAndStatus(Collection<Long> auctionIds, OrderStatus status);

    boolean existsByCourierAndTrackingNo(Courier courier, String trackingNo);

    /**
     * 상품 이미지 삭제 전 확인: 이 판매자의 주문(취소된 주문 포함)이 이 키의 사진을 대표 이미지로 들고 있는지.
     * 주문에는 낙찰 때의 전체 주소(공개 주소 앞부분 + "/" + 키)가 저장되므로, 앞부분(CDN 도메인 등) 설정이 바뀌어도 찾을 수 있게 "/키"로 끝나는지 본다.
     */
    boolean existsBySellerIdAndProductImageUrlEndingWith(Long sellerId, String suffix);

    /** 판매자 프로필 신뢰 지표: 판매자의 주문 중 그 상태인 것의 수 (구매 확정 수) */
    long countBySellerIdAndStatus(Long sellerId, OrderStatus status);

    /** 판매자 프로필 신뢰 지표: 최근 발송한 주문 (주문부터 발송까지 걸린 시간 평균) */
    List<Order> findTop100BySellerIdAndShippedAtIsNotNullOrderByIdDesc(Long sellerId);

    /** 배송지 입력 기한이 지났는데 배송지가 없는 주문 */
    @Query("""
            select o.id from Order o
            where o.status = com.example.ddangddangddang.domain.order.entity.OrderStatus.ESCROW
              and o.address is null and o.addressDeadlineAt <= :now
              and o.id > :afterId
            order by o.id
            """)
    List<Long> findAddressOverdueIds(@Param("now") LocalDateTime now, @Param("afterId") long afterId, Pageable pageable);

    /** 발송 기한이 지났는데 발송하지 않은 주문 */
    @Query("""
            select o.id from Order o
            where o.status = com.example.ddangddangddang.domain.order.entity.OrderStatus.ESCROW
              and o.shipDeadlineAt <= :now
              and o.id > :afterId
            order by o.id
            """)
    List<Long> findShipOverdueIds(@Param("now") LocalDateTime now, @Param("afterId") long afterId, Pageable pageable);

    /** 구매 내역. cursor보다 오래된 주문을 최신순으로 조회한다. status, cursor가 null이면 조건을 적용하지 않는다. */
    @Query("""
            select o from Order o
            where o.buyerId = :userId
              and (:status is null or o.status = :status)
              and (:cursor is null or o.id < :cursor)
            order by o.id desc
            """)
    List<Order> findBuyerOrders(@Param("userId") Long userId,
                                @Param("status") OrderStatus status,
                                @Param("cursor") Long cursor,
                                Pageable pageable);

    /** 판매 내역. 조건은 구매 내역과 같다. */
    @Query("""
            select o from Order o
            where o.sellerId = :userId
              and (:status is null or o.status = :status)
              and (:cursor is null or o.id < :cursor)
            order by o.id desc
            """)
    List<Order> findSellerOrders(@Param("userId") Long userId,
                                 @Param("status") OrderStatus status,
                                 @Param("cursor") Long cursor,
                                 Pageable pageable);

    /**
     * 상태를 바꾸는 작업(발송, 구매 확정)은 이 메서드로 주문을 잠근 뒤 처리한다.
     * 구매 확정 버튼을 연타해도 정산이 두 번 되지 않도록 하기 위함이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);

    // 발송 후 기한이 지나도 구매 확정 되지 않은 주문 ID (자동 구매 확정 대상)
    // 관리자 확인 대기 중인 주문과, 배송 중이라 확인을 미룬 주문(다음 확인 시각 전)은 뺀다.
    @Query("""
            select o.id from Order o
            where o.status = :status and o.shippedAt <= :cutoff
            and o.reviewReason is null
            and (o.autoConfirmNextCheckAt is null or o.autoConfirmNextCheckAt <= :now)
            and o.autoConfirmFailCount < :maxFailCount
            and (o.autoConfirmLastFailedAt is null or o.autoConfirmLastFailedAt <= :retryAfter)
            order by o.id
            """)
    List<Long> findConfirmOverdueIds(@Param("status") OrderStatus status,
                                     @Param("cutoff") LocalDateTime cutoff,
                                     @Param("now") LocalDateTime now,
                                     @Param("maxFailCount") int maxFailCount,
                                     @Param("retryAfter") LocalDateTime retryAfter,
                                     Pageable pageable);

    /** 관리자 확인 대기 주문. 먼저 들어온 순 (cursor: 이전 페이지 마지막 주문 ID) */
    @Query("""
            select o from Order o
            where o.status = com.example.ddangddangddang.domain.order.entity.OrderStatus.SHIPPING
            and o.reviewReason is not null and o.reviewResult is null
            and (:cursor is null or o.id > :cursor)
            order by o.id asc
            """)
    List<Order> findReviewOrders(@Param("cursor") Long cursor, Pageable pageable);
}