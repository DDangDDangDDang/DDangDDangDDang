package com.example.ddangddangddang.domain.notification.repository;

import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.notification.entity.AuctionAlert;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AuctionAlertRepository extends JpaRepository<AuctionAlert, Long> {

    boolean existsByUserIdAndAuctionId(Long userId, Long auctionId);

    // 내가 알림 받기를 한 경매 중 statuses 상태인 것 (최근에 받기로 한 것부터). cursor는 이전 페이지 마지막 AuctionAlert ID
    @Query("""
            select a from AuctionAlert a, Auction au
            where au.id = a.auctionId and a.userId = :userId and au.status in :statuses
              and (:cursor is null or a.id < :cursor)
            order by a.id desc""")
    List<AuctionAlert> findMine(@Param("userId") Long userId, @Param("statuses") Collection<AuctionStatus> statuses,
                                @Param("cursor") Long cursor, Pageable pageable);

    /**
     * 끝난 지(낙찰·유찰은 마감 시각, 취소는 취소 시각) before보다 오래된 경매의 알림 받기를 지운다. (NotificationCleaner)
     * 끝난 경매도 결과를 볼 수 있게 한동안 목록에 남겨 두었다가 정리한다.
     */
    @Modifying
    @Query("""
            delete from AuctionAlert a where a.auctionId in (
                select au.id from Auction au
                where (au.status in :ended and au.endAt < :before)
                   or (au.status = :canceled and au.canceledAt < :before))""")
    int deleteForAuctionsEndedBefore(@Param("ended") Collection<AuctionStatus> ended, @Param("canceled") AuctionStatus canceled,
                                     @Param("before") LocalDateTime before);

    @Modifying
    @Query("delete from AuctionAlert a where a.userId = :userId and a.auctionId = :auctionId")
    int deleteMine(@Param("userId") Long userId, @Param("auctionId") Long auctionId);

    @Query("select a.userId from AuctionAlert a where a.auctionId = :auctionId")
    List<Long> findUserIds(@Param("auctionId") Long auctionId);
}
