package com.example.ddangddangddang.domain.notification.repository;

import com.example.ddangddangddang.domain.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 내 알림 최신순. cursor는 이전 페이지 마지막 알림 ID
    @Query("select n from Notification n where n.userId = :userId and (:cursor is null or n.id < :cursor) order by n.id desc")
    List<Notification> findByCursor(@Param("userId") Long userId, @Param("cursor") Long cursor, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /** 보관 기간이 지난 알림 id (NotificationCleaner가 한 번에 지울 만큼씩 찾는다) */
    @Query("select n.id from Notification n where n.createdAt < :before order by n.id")
    List<Long> findIdsOlderThan(@Param("before") LocalDateTime before, Pageable pageable);

    @Modifying
    @Query("delete from Notification n where n.id in :ids")
    int deleteByIds(@Param("ids") List<Long> ids);

    /**
     * 판매자의 since 이후 방송 시작 알림이 새 방송을 가리키게 바꾼다. 30분 안에 다시 시작해 새 알림을 보내지 않을 때,
     * 구독자가 이미 받은 알림을 눌러도 끝난 방송이 아니라 지금 방송으로 가게 한다. 알림 처리는 트랜잭션 밖(@Async)이라 여기서 연다.
     */
    @org.springframework.transaction.annotation.Transactional
    @Modifying
    @Query("""
            update Notification n set n.liveStreamId = :liveStreamId, n.liveTitle = :liveTitle, n.liveRecorded = :liveRecorded
            where n.sellerId = :sellerId
              and n.type = com.example.ddangddangddang.domain.notification.entity.NotificationType.LIVE_STARTED
              and n.createdAt > :since
            """)
    int moveLiveStartedTo(@Param("sellerId") Long sellerId, @Param("since") LocalDateTime since,
                          @Param("liveStreamId") Long liveStreamId, @Param("liveTitle") String liveTitle,
                          @Param("liveRecorded") Boolean liveRecorded);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int readAll(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
