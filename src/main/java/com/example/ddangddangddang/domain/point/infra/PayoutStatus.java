package com.example.ddangddangddang.domain.point.infra;

public enum PayoutStatus {
    SUCCESS,      // 송금 완료
    FAILED,       // 송금이 확실히 실패 (계좌 없음, 예금주 불일치 등)
    IN_PROGRESS,  // 처리 중이거나 결과를 알 수 없음
    NOT_FOUND     // 조회 시: 송금사에 요청 기록이 없음 (요청이 도착하지 않음)
}
