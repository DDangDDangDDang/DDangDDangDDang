package com.example.ddangddangddang.domain.donation.entity;

public enum DonationPointHistoryType {
    EARN,     // 구매 확정 적립: 선물할 포인트 +
    GIFT,     // 선물함: 선물할 포인트 -
    RECEIVE,  // 선물받음: 받은 포인트 +
    EXPIRE,   // 유효기간 만료: 선물할 포인트 -
    SPEND     // 판매자 상점 구매: 받은 포인트 -
}
