package com.example.ddangddangddang.domain.donation.repository;

import com.example.ddangddangddang.domain.donation.entity.Donation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface DonationRepository extends JpaRepository<Donation, Long> {

    Optional<Donation> findByGiverIdAndRequestKey(Long giverId, String requestKey);

    /** 이 사람이 이 판매자에게 from ~ to(제외) 사이에 선물한 합계 (한 달 선물 한도) */
    @Query("""
            select coalesce(sum(d.amount), 0) from Donation d
            where d.giverId = :giverId and d.sellerId = :sellerId and d.createdAt >= :from and d.createdAt < :to
            """)
    long sumGivenBetween(@Param("giverId") Long giverId, @Param("sellerId") Long sellerId,
                         @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 판매자 신뢰 지표: since 이후 선물한 서로 다른 사람 수 */
    @Query("select count(distinct d.giverId) from Donation d where d.sellerId = :sellerId and d.createdAt >= :since")
    long countDonorsSince(@Param("sellerId") Long sellerId, @Param("since") LocalDateTime since);

    /** 판매자 신뢰 지표: since 이후 받은 포인트 합계 */
    @Query("select coalesce(sum(d.amount), 0) from Donation d where d.sellerId = :sellerId and d.createdAt >= :since")
    long sumReceivedSince(@Param("sellerId") Long sellerId, @Param("since") LocalDateTime since);
}
