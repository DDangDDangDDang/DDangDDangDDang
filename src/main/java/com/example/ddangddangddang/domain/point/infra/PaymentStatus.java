package com.example.ddangddangddang.domain.point.infra;

public enum PaymentStatus {
    READY,                  // 결제창만 열린 상태
    PENDING,                // 결제 승인 대기
    VIRTUAL_ACCOUNT_ISSUED, // 가상계좌 발급, 입금 전
    PAID,                   // 결제 완료
    FAILED,                 // 결제 실패
    PARTIAL_CANCELLED,      // 부분 취소
    CANCELLED,              // 전액 취소
    UNKNOWN;                // 위에 없는 값이 오면 여기로

    public static PaymentStatus from(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        try {
            return PaymentStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    /** 다시는 PAID가 될 수 없는 상태 */
    public boolean isTerminalFailure() {
        return this == FAILED || this == CANCELLED || this == PARTIAL_CANCELLED;
    }
}
