package com.example.ddangddangddang.domain.donation.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 선물·상점 구매 한 건이 어느 적립 묶음에서 얼마씩 썼는지. (환불·조사 때 되짚기 위해 남긴다) */
@Getter
@Entity
@Table(name = "donation_lot_usages", indexes = @Index(name = "idx_donation_lot_usage_ref", columnList = "type, ref_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DonationLotUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // MySQL ENUM이 아니라 문자열로 둔다. (값을 더할 때 ddl-auto가 ENUM 정의를 고치지 않음)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private DonationLotUsageType type;

    // type이 GIFT면 선물 ID, SPEND면 판매자 혜택 ID
    @Column(name = "ref_id", nullable = false)
    private Long refId;

    @Column(name = "lot_id", nullable = false)
    private Long lotId;

    @Column(nullable = false)
    private long amount;

    public DonationLotUsage(DonationLotUsageType type, Long refId, Long lotId, long amount) {
        this.type = type;
        this.refId = refId;
        this.lotId = lotId;
        this.amount = amount;
    }
}
