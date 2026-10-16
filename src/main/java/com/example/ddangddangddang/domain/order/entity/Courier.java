package com.example.ddangddangddang.domain.order.entity;

/** 택배사. 송장번호는 택배사마다 체계가 달라서 택배사와 함께 있어야 배송 조회를 할 수 있다. */
public enum Courier {
    CJ,       // CJ대한통운
    LOTTE,    // 롯데택배
    HANJIN,   // 한진택배
    LOGEN,    // 로젠택배
    POST,     // 우체국택배
    ETC
}