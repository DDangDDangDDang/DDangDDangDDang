package com.example.ddangddangddang.domain.directmessage.repository;

import com.example.ddangddangddang.domain.directmessage.entity.DmRoom;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DmRoomRepository extends JpaRepository<DmRoom, Long> {

    Optional<DmRoom> findByUser1IdAndUser2Id(Long user1Id, Long user2Id);

    /**
     * 내 대화방 목록. 메시지가 있는 방만, 최근 대화순(lastMessageId 역순)으로 가져온다.
     * cursor는 이전 페이지 마지막 방의 lastMessageId. null이면 처음부터.
     */
    @Query("""
            select r from DmRoom r
            where (r.user1Id = :userId or r.user2Id = :userId)
              and r.lastMessageId is not null
              and (:cursor is null or r.lastMessageId < :cursor)
            order by r.lastMessageId desc
            """)
    List<DmRoom> findMyRooms(@Param("userId") Long userId, @Param("cursor") Long cursor, Pageable pageable);

    /**
     * 마지막 메시지 정보를 바꾼다. 두 사람이 동시에 보내 순서가 뒤바뀌어도 더 최근(id가 큰) 메시지만 남도록
     * 지금 값보다 큰 id일 때만 바꾼다. 엔티티를 읽어 고치지 않아 동시 전송끼리 충돌하지 않는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update DmRoom r
            set r.lastMessageId = :messageId, r.lastMessagePreview = :preview, r.lastMessageAt = :sentAt
            where r.id = :roomId
              and (r.lastMessageId is null or r.lastMessageId < :messageId)
            """)
    int updateLastMessage(@Param("roomId") Long roomId,
                          @Param("messageId") Long messageId,
                          @Param("preview") String preview,
                          @Param("sentAt") LocalDateTime sentAt);

    /**
     * 읽음 위치를 앞으로만 옮긴다. 늦게 도착한 읽음 요청이 더 앞선 위치로 되돌리지 않도록 지금 값보다 클 때만 바꾼다.
     * 회원이 user1인지 user2인지에 따라 둘 중 하나를 쓴다.
     *
     * @return 바뀌었으면 1, 이미 그 위치 이상까지 읽었으면 0
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update DmRoom r set r.user1LastReadMessageId = :messageId
            where r.id = :roomId
              and (r.user1LastReadMessageId is null or r.user1LastReadMessageId < :messageId)
            """)
    int markReadByUser1(@Param("roomId") Long roomId, @Param("messageId") Long messageId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update DmRoom r set r.user2LastReadMessageId = :messageId
            where r.id = :roomId
              and (r.user2LastReadMessageId is null or r.user2LastReadMessageId < :messageId)
            """)
    int markReadByUser2(@Param("roomId") Long roomId, @Param("messageId") Long messageId);
}
