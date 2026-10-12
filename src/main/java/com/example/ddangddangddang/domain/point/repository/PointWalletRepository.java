package com.example.ddangddangddang.domain.point.repository;

import com.example.ddangddangddang.domain.point.entity.PointWallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointWalletRepository extends JpaRepository<PointWallet, Long> {

    Optional<PointWallet> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}