package com.example.ddangddangddang.domain.donation.repository;

import com.example.ddangddangddang.domain.donation.entity.DonationLotUsage;
import com.example.ddangddangddang.domain.donation.entity.DonationLotUsageType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DonationLotUsageRepository extends JpaRepository<DonationLotUsage, Long> {

    List<DonationLotUsage> findByTypeAndRefId(DonationLotUsageType type, Long refId);
}
