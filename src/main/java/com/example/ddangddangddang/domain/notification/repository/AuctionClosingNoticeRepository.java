package com.example.ddangddangddang.domain.notification.repository;

import com.example.ddangddangddang.domain.notification.entity.AuctionClosingNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuctionClosingNoticeRepository extends JpaRepository<AuctionClosingNotice, Long> {

    /** 마감 임박 알림 기록은 경매가 끝나면 쓸 일이 없어 오래된 것을 지운다. (NotificationCleaner) */
    @Modifying
    @Query("delete from AuctionClosingNotice n where n.notifiedAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
