package com.example.ddangddangddang.domain.auction.repository;

import com.example.ddangddangddang.domain.auction.entity.Bid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BidRepository extends JpaRepository<Bid, Long> {

    /** 경매의 입찰 기록. cursor보다 id가 작은 것을 최신순으로 */
    @Query("""
            select b from Bid b
            where b.auctionId = :auctionId
              and (:cursor is null or b.id < :cursor)
            order by b.id desc
            """)
    List<Bid> findByCursor(@Param("auctionId") Long auctionId,
                           @Param("cursor") Long cursor,
                           Pageable pageable);

    long countByAuctionId(Long auctionId);
}
