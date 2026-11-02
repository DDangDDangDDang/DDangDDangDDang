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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAddressService {
    private static final int MAX_ADDRESS_COUNT = 10;

    private final UserAddressRepository userAddressRepository;

    //배송지 저장
    @Transactional
    public UserAddressResponse createAddress(Long userId, UserAddressRequest request){
        long addressCount = userAddressRepository.countByUserId(userId);
        if (addressCount >= MAX_ADDRESS_COUNT){
            throw new BusinessException(UserAddressErrorCode.ADDRESS_LIMIT_EXCEEDED);
        }
       boolean firstAddress = addressCount == 0;

        UserAddress userAddress = UserAddress.create(userId, request.addressName(), request.receiverName(), request.receiverPhone(), request.address(), request.deliveryMemo(), firstAddress);
        return UserAddressResponse.from(userAddressRepository.save(userAddress));
    }
}
