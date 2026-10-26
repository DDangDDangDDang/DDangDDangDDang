package com.example.ddangddangddang.domain.donation.repository;

import com.example.ddangddangddang.domain.donation.entity.DonationPointHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DonationPointHistoryRepository extends JpaRepository<DonationPointHistory, Long> {

    /** 최근 내역부터. cursor는 이전 페이지 마지막 내역 ID (없으면 처음부터) */
    @Query("""
            select h from DonationPointHistory h
            where h.userId = :userId and (:cursor is null or h.id < :cursor)
            order by h.id desc
            """)
    List<DonationPointHistory> findByCursor(@Param("userId") Long userId, @Param("cursor") Long cursor, Pageable pageable);
}
