package com.example.ddangddangddang.domain.shop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상점 전체에 하나뿐인 자리를 나눌 때 잡는 잠금 행. (예: 경매 위로 올리기 동시 10개)
 * 자리 수를 센 뒤 저장하는 사이에 다른 판매자가 끼어들어 상한을 넘지 않도록, 이 행을 잠근 채 세고 저장한다.
 * 회원 지갑 잠금 → 이 행 잠금 순서로만 잡아 데드락이 나지 않는다.
 */
@Getter
@Entity
@Table(name = "shop_locks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopLock {

    public static final String AUCTION_BOOST = "AUCTION_BOOST";

    @Id
    @Column(length = 30)
    private String name;

    public ShopLock(String name) {
        this.name = name;
    }
}
