package com.example.ddangddangddang.domain.shop.repository;

import com.example.ddangddangddang.domain.shop.entity.ShopLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ShopLockRepository extends JpaRepository<ShopLock, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from ShopLock l where l.name = :name")
    Optional<ShopLock> findForUpdate(@Param("name") String name);
}
