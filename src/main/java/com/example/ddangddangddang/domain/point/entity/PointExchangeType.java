package com.example.ddangddangddang.domain.point.entity;

/**
 * 환전 상태.
 *
 * PENDING ──startProcessing──▶ PROCESSING ──complete──▶ COMPLETE
 *    │                              │
 *    └─cancel─▶ CANCELLED           └─fail─▶ FAILED
 *
 * FAILED, CANCELLED가 되면 차감했던 포인트를 돌려준다.
 */
public enum PointExchangeType {
    PENDING,     // 송금 대기. 이 상태일 때만 사용자가 취소할 수 있다
    PROCESSING,  // 송금 요청 중. 결과를 모르는 경우에도 이 상태로 남는다
    COMPLETE,    // 송금 완료
    FAILED,      // 송금 실패. 포인트 환불됨
    CANCELLED    // 사용자 취소. 포인트 환불됨
}
