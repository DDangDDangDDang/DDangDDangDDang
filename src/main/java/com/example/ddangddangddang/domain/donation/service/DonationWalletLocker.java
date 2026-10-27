package com.example.ddangddangddang.domain.donation.service;

import com.example.ddangddangddang.domain.donation.entity.DonationWallet;
import com.example.ddangddangddang.domain.donation.repository.DonationWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 도네이션 지갑 준비(없으면 만들기)와 잠그기. 기존 회원도 쓸 수 있도록 처음 적립·선물·구매할 때 지갑을 만든다.
 *
 * 순서: 트랜잭션 밖에서 prepare로 지갑을 만들어 두고 → 트랜잭션 안에서 lock으로 잠근다.
 * - 트랜잭션 안에서 만들지 않는다. 만들다 유니크 제약(user_id)에 걸리면(동시에 처음 쓰는 두 요청) 그 트랜잭션 전체가 롤백 표시된다.
 *   그렇다고 트랜잭션 안에서 따로 된 트랜잭션(REQUIRES_NEW)으로 만들면 요청 하나가 DB 연결을 두 개 써서,
 *   동시 요청이 많을 때 연결 풀이 바닥난다. (DonationConcurrencyTest)
 * - 없는 행을 잠그지 않는다. MySQL은 없는 행을 SELECT ... FOR UPDATE 하면 그 자리에 갭 잠금을 걸어, 다른 요청의 지갑 만들기를 막는다.
 */
@Component
@RequiredArgsConstructor
public class DonationWalletLocker {

    private final DonationWalletRepository walletRepository;

    /** 트랜잭션 밖에서 부른다. 지갑이 없으면 만든다. (각자 짧은 트랜잭션) */
    public void prepare(Long... userIds) {
        for (Long userId : userIds) {
            if (walletRepository.existsByUserId(userId)) {
                continue;
            }
            try {
                walletRepository.saveAndFlush(DonationWallet.create(userId));
            } catch (DataIntegrityViolationException e) {
                // 다른 요청이 먼저 만들었다.
            }
        }
    }

    /** 트랜잭션 안에서 부른다. prepare로 만들어 둔 지갑을 잠근다. */
    public DonationWallet lock(Long userId) {
        return walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("도네이션 지갑을 먼저 만들어야 합니다. userId=" + userId));
    }

    /**
     * 지갑 두 개를 잠근다. 선물(보낸 사람·판매자)처럼 두 지갑을 함께 바꿀 때, 서로 반대로 선물하는 요청이 엇갈려
     * 데드락이 나지 않도록 항상 userId가 작은 지갑부터 잠근다. (현금 포인트 PointService.replaceHold와 같은 방식)
     */
    public Map<Long, DonationWallet> lockBoth(Long userId, Long otherUserId) {
        List<Long> ids = Stream.of(userId, otherUserId).distinct().sorted().toList();
        Map<Long, DonationWallet> wallets = new HashMap<>();
        ids.forEach(id -> wallets.put(id, lock(id)));
        return wallets;
    }
}
