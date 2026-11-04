package com.example.ddangddangddang.domain.point.repository;

import com.example.ddangddangddang.domain.point.entity.PointCharge;
import com.example.ddangddangddang.domain.point.entity.PointChargeType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PointChargeRepository extends JpaRepository<PointCharge, Long> {

    Optional<PointCharge> findByPaymentId(String paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PointCharge c where c.paymentId = :paymentId")
    Optional<PointCharge> findByPaymentIdForUpdate(@Param("paymentId") String paymentId);

    /** 결제 확인 보정 대상: from ~ to 사이에 만든 이 상태의 충전 중 afterId보다 큰 것. ID 순으로 */
    @Query("""
            select c from PointCharge c
            where c.status = :status and c.createdAt >= :from and c.createdAt < :to and c.id > :afterId
            order by c.id asc
            """)
    List<PointCharge> findCreatedBetween(@Param("status") PointChargeType status,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to,
                                         @Param("afterId") Long afterId,
                                         Pageable pageable);
}
