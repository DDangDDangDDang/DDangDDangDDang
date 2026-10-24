package com.example.ddangddangddang.domain.donation.policy;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DonationPolicyVersionRepository extends JpaRepository<DonationPolicyVersion, Long> {

    /** 지금 정책 (가장 최근에 바꾼 것) */
    Optional<DonationPolicyVersion> findTopByOrderByIdDesc();

    /** 바꾼 기록. 최근부터, cursor는 이전 페이지 마지막 ID */
    @Query("select v from DonationPolicyVersion v where (:cursor is null or v.id < :cursor) order by v.id desc")
    List<DonationPolicyVersion> findByCursor(@Param("cursor") Long cursor, Pageable pageable);
}
