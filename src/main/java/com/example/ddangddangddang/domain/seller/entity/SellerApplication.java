package com.example.ddangddangddang.domain.seller.entity;

import com.example.ddangddangddang.domain.seller.exception.SellerErrorCode;
import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import com.example.ddangddangddang.global.exception.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 판매자 신청. 회원당 하나이고, 반려되면 같은 신청을 고쳐 다시 낸다.
 * 승인(APPROVED)된 회원은 판매자 인증 마크가 붙고 판매자 프로필을 보여 준다.
 * 지금은 인증 전에도 상품 판매·방송을 할 수 있다. (인증은 시청자에게 보여 주는 신뢰 표시)
 */
@Entity
@Table(
        name = "seller_applications",
        uniqueConstraints = @UniqueConstraint(name = "uk_seller_application_user", columnNames = "user_id"),
        indexes = @Index(name = "idx_seller_application_status", columnList = "status, id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerApplication extends BaseTimeEntity {

    public static final int STORE_NAME_MAX_LENGTH = 30;
    public static final int INTRODUCTION_MAX_LENGTH = 500;
    public static final int REJECT_REASON_MAX_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 상점 이름 (프로필에 닉네임과 함께 보여 줌)
    @Column(name = "store_name", nullable = false, length = STORE_NAME_MAX_LENGTH)
    private String storeName;

    // 판매자 소개 (어떤 상품을 파는지)
    @Column(nullable = false, length = INTRODUCTION_MAX_LENGTH)
    private String introduction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SellerApplicationStatus status;

    // 반려 사유. 반려됐을 때만 있다.
    @Column(name = "reject_reason", length = REJECT_REASON_MAX_LENGTH)
    private String rejectReason;

    // 승인·반려한 시각. 승인된 회원은 이 시각이 판매자 인증일이다.
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    // 두 관리자가 같은 신청을 동시에 심사하거나, 재신청과 심사가 겹치면 한쪽만 성공시키기 위한 낙관적 락
    @Version
    private Long version;

    private SellerApplication(Long userId, String storeName, String introduction) {
        this.userId = userId;
        this.storeName = storeName;
        this.introduction = introduction;
        this.status = SellerApplicationStatus.PENDING;
    }

    public static SellerApplication create(Long userId, String storeName, String introduction) {
        return new SellerApplication(userId, storeName, introduction);
    }

    /** 반려된 신청을 고쳐 다시 낸다. 심사 중이거나 승인된 신청은 다시 낼 수 없다. */
    public void resubmit(String storeName, String introduction) {
        if (status != SellerApplicationStatus.REJECTED) {
            throw new BusinessException(status == SellerApplicationStatus.APPROVED
                    ? SellerErrorCode.ALREADY_VERIFIED : SellerErrorCode.APPLICATION_PENDING);
        }
        this.storeName = storeName;
        this.introduction = introduction;
        this.status = SellerApplicationStatus.PENDING;
        this.rejectReason = null;
        this.reviewedAt = null;
    }

    public void approve(LocalDateTime now) {
        validatePending();
        this.status = SellerApplicationStatus.APPROVED;
        this.reviewedAt = now;
    }

    public void reject(String reason, LocalDateTime now) {
        validatePending();
        this.status = SellerApplicationStatus.REJECTED;
        this.rejectReason = reason;
        this.reviewedAt = now;
    }

    public boolean isApproved() {
        return status == SellerApplicationStatus.APPROVED;
    }

    private void validatePending() {
        if (status != SellerApplicationStatus.PENDING) {
            throw new BusinessException(SellerErrorCode.ALREADY_REVIEWED);
        }
    }
}
