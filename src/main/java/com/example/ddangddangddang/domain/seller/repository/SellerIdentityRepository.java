package com.example.ddangddangddang.domain.seller.repository;

import com.example.ddangddangddang.domain.seller.entity.SellerIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SellerIdentityRepository extends JpaRepository<SellerIdentity, Long> {

    Optional<SellerIdentity> findByUserId(Long userId);

    Optional<SellerIdentity> findByCiHash(String ciHash);

    boolean existsByUserId(Long userId);

    List<SellerIdentity> findAllByUserIdIn(Collection<Long> userIds);
}
