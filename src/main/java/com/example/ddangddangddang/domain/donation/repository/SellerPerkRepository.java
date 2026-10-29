package com.example.ddangddangddang.domain.donation.repository;

import com.example.ddangddangddang.domain.donation.entity.SellerPerk;
import com.example.ddangddangddang.domain.donation.entity.SellerPerkType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;

public interface SellerPerkRepository extends JpaRepository<SellerPerk, Long> {

    boolean existsBySellerIdAndType(Long sellerId, SellerPerkType type);

    /** sellerIds 중 지금 이 혜택이 진행 중인 판매자들 */
    @Query("""
            select distinct p.sellerId from SellerPerk p
            where p.type = :type and p.startsAt <= :now and p.endsAt > :now and p.sellerId in :sellerIds
            """)
    Set<Long> findActiveSellerIdsAmong(@Param("type") SellerPerkType type, @Param("now") LocalDateTime now,
                                       @Param("sellerIds") Collection<Long> sellerIds);
}
