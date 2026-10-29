package com.example.ddangddangddang.domain.directmessage.repository;

import com.example.ddangddangddang.domain.directmessage.entity.DmModerationFlag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DmModerationFlagRepository extends JpaRepository<DmModerationFlag, Long> {

    boolean existsByMessageId(Long messageId);

    /**
     * 관리자 AI 검토 목록. 최근 표시부터, cursor보다 id가 작은 것을 가져온다.
     */
    @Query("""
            select f from DmModerationFlag f
            where (:cursor is null or f.id < :cursor)
            order by f.id desc
            """)
    List<DmModerationFlag> findByCursor(@Param("cursor") Long cursor, Pageable pageable);
}
