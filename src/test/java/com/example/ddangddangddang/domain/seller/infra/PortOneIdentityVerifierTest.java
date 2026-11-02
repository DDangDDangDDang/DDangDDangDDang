package com.example.ddangddangddang.domain.seller.infra;

import com.example.ddangddangddang.domain.seller.exception.SellerErrorCode;
import com.example.ddangddangddang.domain.seller.infra.PortOneIdentityVerifier.Customer;
import com.example.ddangddangddang.domain.seller.infra.PortOneIdentityVerifier.PortOneIdentityVerification;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortOneIdentityVerifierTest {

    @Test
    @DisplayName("인증 완료(VERIFIED) 응답에서 이름·생년월일·CI를 꺼낸다")
    void verified() {
        var response = new PortOneIdentityVerification("id-1", "VERIFIED", new Customer(" 홍길동 ", "1990-01-31", "ci-1"));

        VerifiedIdentity identity = PortOneIdentityVerifier.toVerifiedIdentity("id-1", response);

        assertThat(identity).isEqualTo(new VerifiedIdentity("홍길동", LocalDate.of(1990, 1, 31), "ci-1"));
    }

    @Test
    @DisplayName("인증이 끝나지 않은 응답(READY·FAILED)은 SELLER_011")
    void notVerified() {
        var ready = new PortOneIdentityVerification("id-1", "READY", null);

        assertThatThrownBy(() -> PortOneIdentityVerifier.toVerifiedIdentity("id-1", ready))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(SellerErrorCode.IDENTITY_NOT_VERIFIED);
    }

    @Test
    @DisplayName("CI나 생년월일이 빠진 응답은 받지 않는다 (SELLER_015)")
    void missingFields() {
        var noCi = new PortOneIdentityVerification("id-1", "VERIFIED", new Customer("홍길동", "1990-01-31", null));

        assertThatThrownBy(() -> PortOneIdentityVerifier.toVerifiedIdentity("id-1", noCi))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(SellerErrorCode.IDENTITY_GATEWAY_ERROR);
    }
}
