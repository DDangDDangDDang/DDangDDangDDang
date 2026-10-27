package com.example.ddangddangddang.domain.directmessage.repository;

import com.example.ddangddangddang.domain.directmessage.entity.DmReport;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DmReportRepository extends JpaRepository<DmReport, Long> {

    boolean existsByReporterIdAndMessageId(Long reporterId, Long messageId);

    /**
     * 관리자 신고 목록. 최근 신고부터, cursor보다 id가 작은 것을 가져온다.
     */
    @Query("""
            select r from DmReport r
            where (:cursor is null or r.id < :cursor)
            order by r.id desc
            """)
    List<DmReport> findByCursor(@Param("cursor") Long cursor, Pageable pageable);
}
