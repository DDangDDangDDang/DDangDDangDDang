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
}
