package com.example.ddangddangddang.domain.notification.repository;

import com.example.ddangddangddang.domain.notification.entity.AuctionAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuctionAlertRepository extends JpaRepository<AuctionAlert, Long> {

    boolean existsByUserIdAndAuctionId(Long userId, Long auctionId);

    @Modifying
    @Query("delete from AuctionAlert a where a.userId = :userId and a.auctionId = :auctionId")
    int deleteMine(@Param("userId") Long userId, @Param("auctionId") Long auctionId);

    @Query("select a.userId from AuctionAlert a where a.auctionId = :auctionId")
    List<Long> findUserIds(@Param("auctionId") Long auctionId);
}
