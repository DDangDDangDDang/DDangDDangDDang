package com.example.ddangddangddang.domain.order.entity;

public enum OrderDelayStatus {
    PENDING,   // 구매자 응답 대기
    APPROVED,  // 승인, 발송 기한 7일 연장
    REJECTED   // 거절, 기존 기한 유지
}
