package com.example.ddangddangddang.domain.seller.entity;

import com.example.ddangddangddang.domain.seller.exception.SellerErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SellerApplicationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 12, 0);

    @Test
    @DisplayName("반려된 신청은 고쳐 다시 내면 심사 중이 되고 반려 사유가 지워진다")
    void resubmitAfterReject() {
        SellerApplication application = SellerApplication.create(1L, "상점", "소개");
        application.reject("소개를 더 적어 주세요.", NOW);

        application.resubmit("새 상점", "자세한 소개");

        assertThat(application.getStatus()).isEqualTo(SellerApplicationStatus.PENDING);
        assertThat(application.getStoreName()).isEqualTo("새 상점");
        assertThat(application.getRejectReason()).isNull();
        assertThat(application.getReviewedAt()).isNull();
    }

    @Test
    @DisplayName("심사 중이면 SELLER_001, 승인됐으면 SELLER_002로 다시 낼 수 없다")
    void cannotResubmit() {
        SellerApplication pending = SellerApplication.create(1L, "상점", "소개");
        SellerApplication approved = SellerApplication.create(2L, "상점", "소개");
        approved.approve(NOW);

        assertThatThrownBy(() -> pending.resubmit("상점", "소개"))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(SellerErrorCode.APPLICATION_PENDING);
        assertThatThrownBy(() -> approved.resubmit("상점", "소개"))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(SellerErrorCode.ALREADY_VERIFIED);
    }

    @Test
    @DisplayName("이미 심사한 신청은 다시 승인·반려할 수 없다 (SELLER_004)")
    void reviewOnce() {
        SellerApplication application = SellerApplication.create(1L, "상점", "소개");
        application.approve(NOW);

        assertThat(application.isApproved()).isTrue();
        assertThat(application.getReviewedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> application.reject("사유", NOW))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(SellerErrorCode.ALREADY_REVIEWED);
    }
}
