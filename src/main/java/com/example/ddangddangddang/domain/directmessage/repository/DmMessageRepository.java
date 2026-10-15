package com.example.ddangddangddang.domain.directmessage.repository;

import com.example.ddangddangddang.domain.directmessage.entity.DmMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DmMessageRepository extends JpaRepository<DmMessage, Long> {

    Optional<DmMessage> findBySenderIdAndClientMessageId(Long senderId, String clientMessageId);

    /**
     * 이전 메시지 보기. cursor보다 id가 작은(더 오래된) 메시지를 최신순으로 가져온다. cursor가 null이면 가장 최근부터.
     */
    @Query("""
            select m from DmMessage m
            where m.roomId = :roomId
              and (:cursor is null or m.id < :cursor)
            order by m.id desc
            """)
    List<DmMessage> findByCursor(@Param("roomId") Long roomId, @Param("cursor") Long cursor, Pageable pageable);

    /**
     * 방별 읽지 않은 메시지 수. 상대가 보낸 메시지 중 내가 마지막으로 읽은 메시지보다 id가 큰 것을 센다.
     * 목록 한 페이지의 방들을 한 번에 센다. (방마다 따로 세지 않음)
     */
    @Query("""
            select m.roomId as roomId, count(m) as count
            from DmMessage m, DmRoom r
            where m.roomId = r.id
              and r.id in :roomIds
              and m.senderId <> :userId
              and m.id > (case when r.user1Id = :userId
                               then coalesce(r.user1LastReadMessageId, 0)
                               else coalesce(r.user2LastReadMessageId, 0) end)
            group by m.roomId
            """)
    List<RoomUnreadCount> countUnreadByRoom(@Param("userId") Long userId, @Param("roomIds") List<Long> roomIds);

    /**
     * 내 모든 대화방의 읽지 않은 메시지 수 합계. (상단 알림 배지용)
     */
    @Query("""
            select count(m)
            from DmMessage m, DmRoom r
            where m.roomId = r.id
              and (r.user1Id = :userId or r.user2Id = :userId)
              and m.senderId <> :userId
              and m.id > (case when r.user1Id = :userId
                               then coalesce(r.user1LastReadMessageId, 0)
                               else coalesce(r.user2LastReadMessageId, 0) end)
            """)
    long countAllUnread(@Param("userId") Long userId);

    interface RoomUnreadCount {
        Long getRoomId();

        Long getCount();
    }
}
