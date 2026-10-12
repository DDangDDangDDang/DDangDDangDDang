package com.example.ddangddangddang.domain.point.repository;

import com.example.ddangddangddang.domain.point.entity.PointHistory;
import com.example.ddangddangddang.domain.point.entity.PointHistoryType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {

    /**
     * 커서 기반 조회. cursor보다 id가 작은(더 오래된) 내역을 최신순으로 가져온다.
     * type, cursor가 null이면 해당 조건을 적용하지 않는다.
     */
    @Query("""
            select h from PointHistory h
            where h.walletId = :walletId
              and (:type is null or h.type = :type)
              and (:cursor is null or h.id < :cursor)
            order by h.id desc
            """)
    List<PointHistory> findByCursor(@Param("walletId") Long walletId,
                                    @Param("type") PointHistoryType type,
                                    @Param("cursor") Long cursor,
                                    Pageable pageable);
}