package com.example.ddangddangddang.domain.point.repository;

import com.example.ddangddangddang.domain.point.entity.PointCharge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PointChargeRepository extends JpaRepository<PointCharge, Long> {

    Optional<PointCharge> findByPaymentId(String paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PointCharge c where c.paymentId = :paymentId")
    Optional<PointCharge> findByPaymentIdForUpdate(@Param("paymentId") String paymentId);
}
