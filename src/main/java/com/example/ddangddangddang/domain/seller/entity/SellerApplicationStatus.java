package com.example.ddangddangddang.domain.seller.entity;

/** 판매자 신청 상태. 관리자가 승인하면 판매자 인증 회원이 된다. */
public enum SellerApplicationStatus {
    PENDING,   // 심사 중
    APPROVED,  // 승인 (판매자 인증)
    REJECTED   // 반려 (사유를 보고 다시 신청할 수 있음)
}
