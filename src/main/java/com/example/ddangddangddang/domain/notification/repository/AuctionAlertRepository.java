package com.example.ddangddangddang.domain.notification.repository;

import com.example.ddangddangddang.domain.notification.entity.AuctionAlert;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuctionAlertRepository extends JpaRepository<AuctionAlert, Long> {

    boolean existsByUserIdAndAuctionId(Long userId, Long auctionId);

    // 내가 알림 받기를 한 경매 (최근에 받기로 한 것부터). cursor는 이전 페이지 마지막 AuctionAlert ID
    @Query("select a from AuctionAlert a where a.userId = :userId and (:cursor is null or a.id < :cursor) order by a.id desc")
    List<AuctionAlert> findMine(@Param("userId") Long userId, @Param("cursor") Long cursor, Pageable pageable);

    @Modifying
    @Query("delete from AuctionAlert a where a.userId = :userId and a.auctionId = :auctionId")
    int deleteMine(@Param("userId") Long userId, @Param("auctionId") Long auctionId);

    @Query("select a.userId from AuctionAlert a where a.auctionId = :auctionId")
    List<Long> findUserIds(@Param("auctionId") Long auctionId);
}
