package com.example.ddangddangddang.domain.shop.repository;

import com.example.ddangddangddang.domain.shop.entity.AuctionBoost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuctionBoostRepository extends JpaRepository<AuctionBoost, Long> {

    boolean existsByAuctionId(Long auctionId);

    /** 지금 올라가 있는 경매들 (먼저 끝나는 순) */
    @Query("select b from AuctionBoost b where b.endsAt > :now order by b.endsAt asc")
    List<AuctionBoost> findActive(@Param("now") LocalDateTime now);
}
