package com.example.ddangddangddang.domain.donation.entity;

/** 적립 묶음을 어디에 썼는지. DonationLotUsage.refId가 가리키는 대상이 달라진다. */
public enum DonationLotUsageType {
    GIFT,  // 선물 (refId = 선물 ID)
    SPEND, // 예전 판매자 상점 구매 (refId = 판매자 혜택 ID). 지금은 마일리지 상점(SHOP)으로 산다
    SHOP   // 마일리지 상점 구매 (refId = 상점 구매 ID)
}
