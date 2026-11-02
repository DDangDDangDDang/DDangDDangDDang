package com.example.ddangddangddang.domain.address.service;

import com.example.ddangddangddang.domain.address.dto.request.UserAddressRequest;
import com.example.ddangddangddang.domain.address.dto.response.UserAddressResponse;
import com.example.ddangddangddang.domain.address.entity.UserAddress;
import com.example.ddangddangddang.domain.address.exception.UserAddressErrorCode;
import com.example.ddangddangddang.domain.address.repository.UserAddressRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAddressService {
    private static final int MAX_ADDRESS_COUNT = 10;

    private final UserAddressRepository userAddressRepository;

    //배송지 저장
    @Transactional
    public UserAddressResponse createAddress(Long userId, UserAddressRequest request) {
        long addressCount = userAddressRepository.countByUserId(userId);
        if (addressCount >= MAX_ADDRESS_COUNT) {
            throw new BusinessException(UserAddressErrorCode.ADDRESS_LIMIT_EXCEEDED);
        }
        boolean firstAddress = addressCount == 0;

        UserAddress userAddress = UserAddress.create(userId, request.addressName(), request.receiverName(), request.receiverPhone(), request.address(), request.deliveryMemo(), firstAddress);
        return UserAddressResponse.from(userAddressRepository.save(userAddress));
    }

    // 내 배송지 목록(기본 배송지가 맨 위, 나머지는 최근 저장순)
    public List<UserAddressResponse> getAddresses(Long userId) {
        return userAddressRepository.findByUserIdOrderByIsDefaultDescIdDesc(userId).stream()
                .map(UserAddressResponse::from)
                .toList();
    }

    // 내 기본 배송지(주문시 자동 채우기용, 저장된 배송지 없을시 data: null)
    public UserAddressResponse getDefaultAddress(Long userId) {
        return userAddressRepository.findByUserIdAndIsDefaultTrue(userId)
                .map(UserAddressResponse::from)
                .orElse(null);
    }
}
