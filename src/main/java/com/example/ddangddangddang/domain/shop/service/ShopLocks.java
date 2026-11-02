package com.example.ddangddangddang.domain.shop.service;

import com.example.ddangddangddang.domain.shop.entity.ShopLock;
import com.example.ddangddangddang.domain.shop.repository.ShopLockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 상점 잠금 행 준비와 잠그기. (DonationWalletLocker와 같은 방식)
 * 트랜잭션 밖에서 prepare로 행을 만들어 두고, 트랜잭션 안에서 lock으로 잠근다. (없는 행을 잠그면 갭 잠금이 걸려 만들기를 막는다)
 */
@Component
@RequiredArgsConstructor
public class ShopLocks {

    private final ShopLockRepository lockRepository;

    public void prepare(String name) {
        if (lockRepository.existsById(name)) {
            return;
        }
        try {
            lockRepository.saveAndFlush(new ShopLock(name));
        } catch (DataIntegrityViolationException e) {
            // 다른 요청이 먼저 만들었다.
        }
    }

    public void lock(String name) {
        lockRepository.findForUpdate(name)
                .orElseThrow(() -> new IllegalStateException("상점 잠금 행을 먼저 만들어야 합니다. name=" + name));
    }
}
