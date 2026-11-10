package com.example.ddangddangddang.domain.order.entity;

/** 구매자가 신고한 문제의 종류 */
public enum OrderIssueType {
    NOT_RECEIVED,  // 상품을 받지 못함
    WRONG_ITEM,    // 주문한 상품과 다름
    DAMAGED,       // 상품이 파손됨
    OTHER          // 그 밖
}
