package com.example.ddangddangddang.domain.donation.repository;

import com.example.ddangddangddang.domain.donation.entity.DonationLotUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DonationLotUsageRepository extends JpaRepository<DonationLotUsage, Long> {

    List<DonationLotUsage> findByDonationId(Long donationId);
}
