package com.example.ddangddangddang.domain.address.dto.response;

import com.example.ddangddangddang.domain.address.entity.UserAddress;
import com.example.ddangddangddang.domain.review.entity.Review;

import java.time.LocalDateTime;

//배송지 하나
public record UserAddressResponse(
        Long addressId,
        String addressName,
        String receiverName,
        String receiverPhone,
        String address,
        String deliveryMemo,
        boolean isDefault
) {
    public static UserAddressResponse from(UserAddress userAddress) {
        return new UserAddressResponse(
                userAddress.getId(),
                userAddress.getAddressName(),
                userAddress.getReceiverName(),
                userAddress.getReceiverPhone(),
                userAddress.getAddress(),
                userAddress.getDeliveryMemo(),
                userAddress.isDefault()
        );
    }
}
