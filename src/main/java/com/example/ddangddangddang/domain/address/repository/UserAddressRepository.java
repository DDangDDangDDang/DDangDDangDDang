package com.example.ddangddangddang.domain.address.repository;

import com.example.ddangddangddang.domain.address.entity.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Optional;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    // 내 배송지 목록: 기본 배송지는 맨위, 나머지는 최근 저장순
    List<UserAddress> findByUserIdOrderByIsDefaultDescIdDesc(Long userId);

    // 기본 배송지를 (혹시 다중으로 요청시 가장 최근 것 하나만)
    Optional<UserAddress> findFirstByUserIdAndIsDefaultTrueOrderByIdDesc(Long userId);

    // 기본 배송지 전부 (기본 변경시 모두 해제용)
    List<UserAddress> findByUserIdAndIsDefaultTrue(Long userId);

    // 내 배송지 개수( 최대 개수 확인, 첫 배송지인지 확인)
    long countByUserId(Long userId);

    //내 기본 배송지 삭제시 해당 배송지 이외 최근 추가 배송지로 자동설정
    Optional<UserAddress> findFirstByUserIdAndIdNotOrderByIdDesc(Long userId, Long excludedAddressId);
}
