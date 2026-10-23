package com.example.ddangddangddang.domain.donation.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 도네이션 포인트를 바꾸는 트랜잭션. 지갑을 트랜잭션 밖에서 준비(DonationWalletLocker.prepare)한 뒤에 연다.
 *
 * READ_COMMITTED로 연다. 지갑 잠금을 기다리는 동안 먼저 끝난 적립·선물을 한도 계산(이번 달 합계)에 넣기 위해서다.
 * MySQL 기본 REPEATABLE READ에서는 처음 읽은 시점의 스냅샷으로 합계를 세어, 잠금을 기다리는 사이 끝난 것이 빠질 수 있다.
 */
@Component
public class DonationTransactions {

    private final TransactionTemplate template;

    public DonationTransactions(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
        this.template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public <T> T execute(Supplier<T> work) {
        return template.execute(status -> work.get());
    }
}
