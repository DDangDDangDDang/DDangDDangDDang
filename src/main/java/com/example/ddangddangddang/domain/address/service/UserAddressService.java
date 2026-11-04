package com.example.ddangddangddang.domain.address.service;

import com.example.ddangddangddang.domain.address.dto.request.UserAddressRequest;
import com.example.ddangddangddang.domain.address.dto.response.UserAddressResponse;
import com.example.ddangddangddang.domain.address.entity.UserAddress;
import com.example.ddangddangddang.domain.address.exception.UserAddressErrorCode;
import com.example.ddangddangddang.domain.address.repository.UserAddressRepository;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 배송지 추가·삭제·기본 변경은 회원 행을 잠그고 한다. (UserRepository.findByIdForUpdate)
 * 동시에 들어온 요청이 같은 개수를 읽어 최대 개수를 넘거나, 기본 배송지가 둘이 되지 않게 회원 한 명의 요청을 차례로 처리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAddressService {
    private static final int MAX_ADDRESS_COUNT = 10;
    private static final String DEFAULT_ADDRESS_NAME = "기본 배송지";

    private final UserAddressRepository userAddressRepository;
    private final UserRepository userRepository;

    //배송지 저장
    @Transactional
    public UserAddressResponse createAddress(Long userId, UserAddressRequest request) {
        lockUser(userId);
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
        return userAddressRepository.findFirstByUserIdAndIsDefaultTrueOrderByIdDesc(userId)
                .map(UserAddressResponse::from)
                .orElse(null);
    }

    // 주문 배송지 입력 시, 저장된 배송지가 하나도 없으면 그 값을 기본 배송지로 저장
    @Transactional
    public void saveAsDefaultIfNone(Long userId, String receiverName, String receiverPhone,String address, String deliveryMemo){
        lockUser(userId);
        if (userAddressRepository.countByUserId(userId)>0){
            return;
        }
        userAddressRepository.save(UserAddress.create(userId,DEFAULT_ADDRESS_NAME,receiverName,receiverPhone,address,deliveryMemo,true));
    }

    // 배송지 수정 (기본 여부는 바꾸지 않는다)
    @Transactional
    public UserAddressResponse updateAddress(Long userId,Long addressId,UserAddressRequest request){
        // 수정도 잠근다. 기본 변경과 겹치면 수정의 UPDATE가 읽어 둔 이전 기본 여부를 다시 저장해 기본 배송지가 둘이 될 수 있다
        lockUser(userId);
        UserAddress myAddress = findMyAddress(userId, addressId);
        myAddress.update(request.addressName(),request.receiverName(),request.receiverPhone(),request.address(),request.deliveryMemo());
        return UserAddressResponse.from(myAddress);
    }

    //배송지 삭제(기본 배송지를 지우면, 남은 것 중 가장 최근에 저장한 배송지가 기본인 된다)
    @Transactional
    public void deleteAddress(Long userId, Long addressId){
        lockUser(userId);
        UserAddress myAddress = findMyAddress(userId, addressId);
        if (myAddress.isDefault()){
            userAddressRepository.findFirstByUserIdAndIdNotOrderByIdDesc(userId,addressId).ifPresent(UserAddress::makeDefault);
        }
        userAddressRepository.delete(myAddress);
    }

    // 기본 배송지 설정(기존 기본 배송지 해제, 기본은 항상 하나)
    @Transactional
    public UserAddressResponse changeDefaultAddress(Long userId, Long addressId){
        lockUser(userId);
        UserAddress myAddress = findMyAddress(userId, addressId);
        userAddressRepository.findByUserIdAndIsDefaultTrue(userId).forEach(UserAddress::cancelDefault);
        myAddress.makeDefault();
        return UserAddressResponse.from(myAddress);
    }

    // 회원 행을 잠근다. 같은 회원의 배송지 변경은 커밋될 때까지 기다렸다가 바뀐 상태를 보고 처리한다.
    private void lockUser(Long userId) {
        userRepository.findByIdForUpdate(userId);
    }

    // 배송지를 찾고, 내 것인 아닌지
    private UserAddress findMyAddress(Long userId, Long addressId){
        UserAddress userAddress = userAddressRepository.findById(addressId).orElseThrow(
                ()-> new BusinessException(UserAddressErrorCode.ADDRESS_NOT_FOUND)
        );
        if (!userAddress.isOwner(userId)){
            throw new BusinessException(UserAddressErrorCode.NOT_ADDRESS_OWNER);
        }
        return userAddress;
    }
}
