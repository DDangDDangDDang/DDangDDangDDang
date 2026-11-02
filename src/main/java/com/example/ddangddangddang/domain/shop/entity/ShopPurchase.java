package com.example.ddangddangddang.domain.shop.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 마일리지 상점 구매 한 건. 수정·삭제하지 않는다.
 * 마일리지 내역(SHOP)과 묶음 사용 기록이 이 ID를 가리킨다.
 */
@Getter
@Entity
@Table(name = "shop_purchases", indexes = @Index(name = "idx_shop_purchase_user", columnList = "user_id, id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopPurchase extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // MySQL ENUM이 아니라 문자열로 둔다. (상품을 더할 때 ddl-auto가 ENUM 정의를 고치지 않음)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(30)")
    private ShopItem item;

    // 산 가격 (마일리지). 상품 가격이 나중에 바뀌어도 이 구매의 가격은 그대로 남긴다.
    @Column(nullable = false)
    private long price;

    // 구매 내용 (예: 닉네임 변경권으로 바꾼 닉네임). 없으면 null
    @Column(length = 100)
    private String detail;

    private ShopPurchase(Long userId, ShopItem item, long price, String detail) {
        this.userId = userId;
        this.item = item;
        this.price = price;
        this.detail = detail;
    }

    public static ShopPurchase of(Long userId, ShopItem item, String detail) {
        return new ShopPurchase(userId, item, item.price(), detail);
    }
}
