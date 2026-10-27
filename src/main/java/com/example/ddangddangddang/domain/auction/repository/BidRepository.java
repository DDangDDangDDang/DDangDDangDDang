package com.example.ddangddangddang.domain.auction.repository;

import com.example.ddangddangddang.domain.auction.entity.Bid;
import com.example.ddangddangddang.domain.auction.entity.BidStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BidRepository extends JpaRepository<Bid, Long> {

    // 이 경매에 한 번이라도 입찰한 회원 (마감 임박·취소·종료 알림 대상)
    @Query("select distinct b.bidderId from Bid b where b.auctionId = :auctionId")
    List<Long> findBidderIds(@Param("auctionId") Long auctionId);

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

    /**
     * 경매의 최고 입찰(ACTIVE)을 다른 상태로 바꾼다. 상위 입찰·즉시구매면 OUTBID, 마감 낙찰이면 WON.
     * 경매 행을 잠근(조건부 UPDATE 또는 SELECT FOR UPDATE) 트랜잭션에서만 호출한다. 그래야 같은 경매의 입찰 상태 변경이 겹치지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query("update Bid b set b.status = :to where b.auctionId = :auctionId and b.status = :from")
    int changeStatus(@Param("auctionId") Long auctionId,
                     @Param("from") BidStatus from,
                     @Param("to") BidStatus to);

    /** 이 회원이 이 경매에 넣은 가장 높은 입찰 금액. 입찰한 적 없으면 null */
    @Query("select max(b.amount) from Bid b where b.auctionId = :auctionId and b.bidderId = :bidderId")
    Long findMaxAmount(@Param("auctionId") Long auctionId, @Param("bidderId") Long bidderId);
}
