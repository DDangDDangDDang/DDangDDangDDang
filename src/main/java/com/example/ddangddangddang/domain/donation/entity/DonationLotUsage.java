package com.example.ddangddangddang.domain.donation.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 선물 한 건이 어느 적립 묶음에서 얼마씩 썼는지. (환불·조사 때 되짚기 위해 남긴다) */
@Getter
@Entity
@Table(name = "donation_lot_usages", indexes = @Index(name = "idx_donation_lot_usage_donation", columnList = "donation_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DonationLotUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "donation_id", nullable = false)
    private Long donationId;

    @Column(name = "lot_id", nullable = false)
    private Long lotId;

    @Column(nullable = false)
    private long amount;

    public DonationLotUsage(Long donationId, Long lotId, long amount) {
        this.donationId = donationId;
        this.lotId = lotId;
        this.amount = amount;
    }
}
