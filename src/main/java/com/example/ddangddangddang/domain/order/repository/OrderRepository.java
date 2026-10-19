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

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByAuctionId(Long auctionId);
    boolean existsByCourierAndTrackingNo(Courier courier, String trackingNo);

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
}