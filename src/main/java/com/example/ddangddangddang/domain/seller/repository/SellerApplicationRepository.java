package com.example.ddangddangddang.domain.seller.repository;

import com.example.ddangddangddang.domain.seller.entity.SellerApplication;
import com.example.ddangddangddang.domain.seller.entity.SellerApplicationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SellerApplicationRepository extends JpaRepository<SellerApplication, Long> {

    Optional<SellerApplication> findByUserId(Long userId);

    boolean existsByUserIdAndStatus(Long userId, SellerApplicationStatus status);

    // 판매자 인증 회원만 골라낸다. (여러 회원의 인증 마크를 한 번에 붙일 때)
    @Query("select a.userId from SellerApplication a where a.userId in :userIds and a.status = com.example.ddangddangddang.domain.seller.entity.SellerApplicationStatus.APPROVED")
    List<Long> findApprovedUserIds(@Param("userIds") Collection<Long> userIds);

    // 관리자 심사 목록: 상태별(없으면 전체) 최근 신청부터. cursor는 이전 페이지 마지막 신청 ID
    @Query("""
            select a from SellerApplication a
            where (:status is null or a.status = :status) and (:cursor is null or a.id < :cursor)
            order by a.id desc
            """)
    List<SellerApplication> findByCursor(@Param("status") SellerApplicationStatus status, @Param("cursor") Long cursor, Pageable pageable);
}
