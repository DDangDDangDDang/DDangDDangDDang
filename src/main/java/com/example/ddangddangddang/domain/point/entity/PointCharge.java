package com.example.ddangddangddang.domain.point.entity;

import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.exception.CommonErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "point_charges",
        // 결제 확인 보정(PointChargeRecoveryScheduler): 오래된 PENDING 충전을 만든 시각 순으로 찾는다
        indexes = {
                @Index(name = "idx_point_charges_status_created", columnList = "status, created_at"),
                // 결제 취소분을 다 회수하지 못한 충전을 찾는다 (대부분 0이라 거의 비어 있음)
                @Index(name = "idx_point_charges_unreclaimed", columnList = "unreclaimed_amount")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointCharge extends BaseTimeEntity {

    /** 한 번에 충전할 수 있는 금액 (원 = 포인트). 결제 수단 최소 금액과 실수·어뷰징 방지 */
    public static final long MIN_AMOUNT = 1_000L;
    public static final long MAX_AMOUNT = 1_000_000L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, unique = true)
    private String paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointChargeType status;

    /**
     * 충전 완료 뒤 결제사에서 취소(환불)된 금액 합계. 부분 취소가 여러 번 올 수 있어 누적값을 기록하고,
     * 새로 취소된 만큼(포트원의 취소 합계 - 이 값)만 포인트를 회수한다.
     * 상태(status)는 COMPLETE로 둔다. 상태 컬럼이 MySQL ENUM이라 값을 더하면 운영 DB 스키마도 고쳐야 하기 때문이다.
     */
    @Column(nullable = false)
    private long cancelledAmount;

    /**
     * 결제가 취소됐지만 아직 회수하지 못한 포인트. 취소될 때 이미 쓰거나 입찰에 묶여 있어 모자랐던 만큼이다.
     * 지갑의 reclaimDebt와 같이 늘고 줄며, 사용 가능 포인트가 생기면 결제 확인 보정 스케줄러가 마저 회수한다.
     */
    @Column(nullable = false)
    private long unreclaimedAmount;

    private PointCharge(Long userId, Long amount, String paymentId){
        this.userId = userId;
        this.amount = amount;
        this.paymentId = paymentId;
        this.status = PointChargeType.PENDING;
    }

    public static PointCharge create(Long userId, Long amount, String paymentId) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (amount == null || amount <= 0) {
            throw new BusinessException(PointErrorCode.INVALID_AMOUNT);
        }
        if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            throw new BusinessException(PointErrorCode.CHARGE_AMOUNT_OUT_OF_RANGE);
        }
        if (paymentId == null || paymentId.isBlank()) {
            throw new BusinessException(PointErrorCode.INVALID_PAYMENT_ID);
        }

        return new PointCharge(userId, amount, paymentId);
    }

    // ===== 상태 확인 =====

    public boolean isPending() {
        return this.status == PointChargeType.PENDING;
    }

    public boolean isCompleted() {
        return this.status == PointChargeType.COMPLETE;
    }

    // ===== 검증 =====

    // 본인인증(본인 소유 권한 확인)
    public void validateOwner(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new BusinessException(PointErrorCode.CHARGE_ACCESS_DENIED);
        }
    }

    // PG사와 실결제금액 대조
    public boolean matchesAmount(long paidAmount) {
        return this.amount == paidAmount;
    }

    /**
     * 결제사가 알려 준 취소 합계 중 아직 회수하지 않은 금액. 충전 금액을 넘지 않는다.
     * 완료되지 않은 충전이면 0 (포인트를 준 적이 없음)
     */
    public long newlyCancelled(long paymentCancelledTotal) {
        if (!isCompleted()) {
            return 0;
        }
        return Math.max(0, Math.min(paymentCancelledTotal, amount) - cancelledAmount);
    }

    // ===== 상태 변경 =====

    // 거래 성공 상태변경
    public void complete() {
        validatePending();
        this.status = PointChargeType.COMPLETE;
    }

    // 거래 실패 상태변경
    public void fail() {
        validatePending();
        this.status = PointChargeType.FAILED;
    }

    /** 결제 취소분을 회수 처리했다고 기록한다. (newlyCancelled로 구한 금액) */
    public void recordCancellation(long cancelled) {
        if (!isCompleted()) {
            throw new BusinessException(PointErrorCode.INVALID_CHARGE_STATUS);
        }
        if (cancelled <= 0 || cancelledAmount + cancelled > amount) {
            throw new BusinessException(PointErrorCode.INVALID_AMOUNT);
        }
        this.cancelledAmount += cancelled;
    }

    /** 취소분 중 지금 회수하지 못한 금액을 남긴다. 나중에 collectUnreclaimed로 마저 회수한다. */
    public void recordShortfall(long shortfall) {
        if (shortfall < 0 || unreclaimedAmount + shortfall > cancelledAmount) {
            throw new BusinessException(PointErrorCode.INVALID_AMOUNT);
        }
        this.unreclaimedAmount += shortfall;
    }

    /** 남아 있던 미회수 금액 중 collected만큼 회수했다. */
    public void recordCollected(long collected) {
        if (collected < 0 || collected > unreclaimedAmount) {
            throw new BusinessException(PointErrorCode.INVALID_AMOUNT);
        }
        this.unreclaimedAmount -= collected;
    }

    private void validatePending() {
        if (!isPending()) {
            throw new BusinessException(PointErrorCode.INVALID_CHARGE_STATUS);
        }
    }

}
