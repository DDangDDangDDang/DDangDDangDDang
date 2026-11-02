package com.example.ddangddangddang.domain.address.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_addresses", indexes = @Index(name = "idx_user_addresses_user_id", columnList = "user_id"))
public class UserAddress extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String addressName;

    @Column(nullable = false, length = 20)
    private String receiverName;

    @Column(nullable = false, length = 11)
    private String receiverPhone;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(length = 100)
    private String deliveryMemo;

    @Column(nullable = false)
    private boolean isDefault;

    private UserAddress(Long userId, String addressName, String receiverName, String receiverPhone, String address, String deliveryMemo, boolean isDefault) {
        this.userId = userId;
        this.addressName = addressName;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.address = address;
        this.deliveryMemo = normalizeDeliveryMemo(deliveryMemo);
        this.isDefault = isDefault;
    }

    // 배송지 생성
    public static UserAddress create(Long userId, String addressName, String receiverName, String receiverPhone, String address, String deliveryMemo, boolean isDefault) {
        return new UserAddress(userId, addressName, receiverName, receiverPhone, address, deliveryMemo, isDefault);
    }

    // 배송지 수정
    public void update(String addressName, String receiverName, String receiverPhone, String address, String deliveryMemo) {
        this.addressName = addressName;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.address = address;
        this.deliveryMemo = normalizeDeliveryMemo(deliveryMemo);
    }

    // 내 배송지인지
    public boolean isOwner(Long userId){
        return this.userId.equals(userId);
    }

    // 기본 배송지 지정
    public void makeDefault() {
        this.isDefault = true;
    }

    // 기본 배송지 해제
    public void cancelDefault(){
        this.isDefault = false;
    }

    //빈 내용("",' ')은 null로 통일 저장
    private static String normalizeDeliveryMemo(String deliveryMemo) {
        return (deliveryMemo == null || deliveryMemo.isBlank()) ? null : deliveryMemo;
    }
}
