package com.example.ddangddangddang.domain.point.service;

import com.example.ddangddangddang.domain.point.entity.PointWallet;
import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.domain.point.repository.PointWalletRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointService {

    private final PointWalletRepository pointWalletRepository;

    /**
     * 회원가입과 같은 트랜잭션에서 호출된다. 지갑 생성에 실패하면 회원가입도 함께 롤백된다.
     */
    @Transactional
    public void createWallet(Long userId) {
        if (pointWalletRepository.existsByUserId(userId)) {
            throw new BusinessException(PointErrorCode.WALLET_ALREADY_EXISTS);
        }
        pointWalletRepository.save(PointWallet.create(userId));
    }
}