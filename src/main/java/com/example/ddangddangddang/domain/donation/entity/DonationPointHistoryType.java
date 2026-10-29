package com.example.ddangddangddang.domain.donation.entity;

public enum DonationPointHistoryType {
    EARN,     // 구매 확정 적립: +
    SALE,     // 판매 확정 적립: +
    GIFT,     // 선물함: -
    RECEIVE,  // 선물받음: +
    EXPIRE,   // 유효기간 만료: -
    SPEND,    // 예전 판매자 상점 구매: - (지금은 SHOP)
    SHOP      // 마일리지 상점 구매 (닉네임 변경권 등): -
}
