package com.example.ddangddangddang.domain.order.repository;

import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.entity.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByAuctionId(Long auctionId);

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
}