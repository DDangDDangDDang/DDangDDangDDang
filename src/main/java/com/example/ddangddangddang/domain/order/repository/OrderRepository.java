package com.example.ddangddangddang.domain.order.repository;

import com.example.ddangddangddang.domain.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByAuctionId(Long auctionId);
}