package com.example.ddangddangddang.domain.seller.repository;

import com.example.ddangddangddang.domain.seller.entity.SellerSubscription;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SellerSubscriptionRepository extends JpaRepository<SellerSubscription, Long> {

    boolean existsBySubscriberIdAndSellerId(Long subscriberId, Long sellerId);

    long countBySellerId(Long sellerId);

    @Modifying
    @Query("delete from SellerSubscription s where s.subscriberId = :subscriberId and s.sellerId = :sellerId")
    int deleteBySubscriberIdAndSellerId(@Param("subscriberId") Long subscriberId, @Param("sellerId") Long sellerId);

    // 내가 구독한 판매자 (최근 구독순)
    List<SellerSubscription> findBySubscriberIdOrderByIdDesc(Long subscriberId, Pageable pageable);

    // 방송 시작 알림 대상
    @Query("select s.subscriberId from SellerSubscription s where s.sellerId = :sellerId")
    List<Long> findSubscriberIds(@Param("sellerId") Long sellerId);
}
