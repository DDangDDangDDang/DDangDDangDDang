package com.example.ddangddangddang.domain.point.repository;

import com.example.ddangddangddang.domain.point.entity.PointWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PointWalletRepository extends JpaRepository<PointWallet, Long> {

    Optional<PointWallet> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    /**
     * 잔액을 바꾸는 모든 작업은 이 메서드로 지갑을 조회한다. (SELECT ... FOR UPDATE)
     * 같은 지갑에 대한 다른 트랜잭션은 이 트랜잭션이 끝날 때까지 기다린다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from PointWallet w where w.userId = :userId")
    Optional<PointWallet> findByUserIdForUpdate(@Param("userId") Long userId);
}