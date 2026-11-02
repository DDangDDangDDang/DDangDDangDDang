package com.example.ddangddangddang.domain.address.repository;

import com.example.ddangddangddang.domain.address.entity.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    // 내 배송지 목록: 기본 배송지는 맨위, 나머지는 최근 저장순
    List<UserAddress> findByUserIdOrderByIsDefaultDescIdDesc(Long userId);

    //내 기본 배송지
    Optional<UserAddress> findByUserIdAndIsDefaultTrue(Long userId);

    // 내 배송지 개수( 최대 개수 확인, 첫 배송지인지 확인)
    long countByUserId(Long userId);

    // 기본 배송지를 지울시, 지운 배송지를 제외한 가장 최근 배송지를 자동으로 기본 배송지로 적용
    Optional<UserAddress> findFirstByUserIdAndIdNotOrderByIdDesc(Long userId,Long excludedAddressId);
}
