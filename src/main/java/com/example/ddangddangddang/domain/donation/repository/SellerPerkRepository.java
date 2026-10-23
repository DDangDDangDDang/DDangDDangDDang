package com.example.ddangddangddang.domain.donation.repository;

import com.example.ddangddangddang.domain.donation.entity.SellerPerk;
import com.example.ddangddangddang.domain.donation.entity.SellerPerkType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SellerPerkRepository extends JpaRepository<SellerPerk, Long> {

    /** 이 혜택 중 가장 늦게 끝나는 것 (새로 사면 그 뒤에 이어 붙인다) */
    Optional<SellerPerk> findTopBySellerIdAndTypeOrderByEndsAtDesc(Long sellerId, SellerPerkType type);

    /** 아직 끝나지 않은 내 혜택 (진행 중 + 이어 붙여 기다리는 것) */
    List<SellerPerk> findBySellerIdAndEndsAtAfterOrderByStartsAtAsc(Long sellerId, LocalDateTime now);

    /** 지금 이 혜택이 진행 중인 판매자들 */
    @Query("""
            select distinct p.sellerId from SellerPerk p
            where p.type = :type and p.startsAt <= :now and p.endsAt > :now
            """)
    Set<Long> findActiveSellerIds(@Param("type") SellerPerkType type, @Param("now") LocalDateTime now);

    /** sellerIds 중 지금 이 혜택이 진행 중인 판매자들 */
    @Query("""
            select distinct p.sellerId from SellerPerk p
            where p.type = :type and p.startsAt <= :now and p.endsAt > :now and p.sellerId in :sellerIds
            """)
    Set<Long> findActiveSellerIdsAmong(@Param("type") SellerPerkType type, @Param("now") LocalDateTime now,
                                       @Param("sellerIds") Collection<Long> sellerIds);
}
