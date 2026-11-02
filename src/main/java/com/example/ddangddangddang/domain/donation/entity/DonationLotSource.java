package com.example.ddangddangddang.domain.donation.entity;

/**
 * 적립 묶음이 생긴 곳. 어디서 생겼든 같은 마일리지로, 선물·상점에 똑같이 쓰고 유효기간도 같다.
 */
public enum DonationLotSource {
    PURCHASE, // 구매 확정 적립 (구매자). orderId가 있다
    SALE,     // 판매 확정 적립 (판매자). orderId가 있다
    RECEIVE   // 선물받음 (판매자). donationId가 있다. 마일리지를 하나로 합칠 때 옮긴 받은 마일리지는 donationId가 없다
}
