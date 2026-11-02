package com.example.ddangddangddang.domain.donation.service;

import com.example.ddangddangddang.domain.donation.entity.DonationLotUsageType;
import com.example.ddangddangddang.domain.donation.entity.DonationPointHistory;
import com.example.ddangddangddang.domain.donation.entity.DonationPointHistoryType;
import com.example.ddangddangddang.domain.donation.entity.DonationPointLot;
import com.example.ddangddangddang.domain.donation.entity.DonationWallet;
import com.example.ddangddangddang.domain.donation.repository.DonationPointHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

/**
 * 마일리지 상점 결제. 상점(shop 도메인)이 상품 효과를 적용하는 일과 마일리지를 빼는 일을 한 트랜잭션으로 묶는다.
 *
 * 1. 지갑을 잠근다. 같은 회원의 구매는 한 줄로 처리된다.
 * 2. 상품 효과를 적용하고 구매 기록을 남긴다. (work, 예: 닉네임 바꾸기) 상품 규칙(변경권이 필요한지, 닉네임 중복 등)을 잔액보다 먼저 알려 준다.
 * 3. 마일리지가 충분한지 보고(모자라면 DONATION_002), 유효기간이 먼저 끝나는 묶음부터 빼고 내역(SHOP)을 남긴다.
 * 어느 단계든 실패하면 모두 되돌린다. 효과 적용이 실패하면 마일리지가 빠지지 않고, 마일리지가 모자라면 효과도 적용되지 않는다.
 */
@Component
public class DonationShopPayment {

    private final DonationWalletLocker walletLocker;
    private final DonationLotSpender lotSpender;
    private final DonationPointHistoryRepository historyRepository;
    private final DonationTransactions transactions;
    private final Clock clock;

    @Autowired
    public DonationShopPayment(DonationWalletLocker walletLocker, DonationLotSpender lotSpender,
                               DonationPointHistoryRepository historyRepository, DonationTransactions transactions) {
        this(walletLocker, lotSpender, historyRepository, transactions, Clock.systemDefaultZone());
    }

    DonationShopPayment(DonationWalletLocker walletLocker, DonationLotSpender lotSpender,
                        DonationPointHistoryRepository historyRepository, DonationTransactions transactions, Clock clock) {
        this.walletLocker = walletLocker;
        this.lotSpender = lotSpender;
        this.historyRepository = historyRepository;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * @param work 상품 효과를 적용하고 구매 기록을 남긴다. 구매 기록 ID와 응답을 돌려준다.
     * @return work의 응답
     */
    public <T> T pay(Long userId, long price, Supplier<Charge<T>> work) {
        walletLocker.prepare(userId);
        return transactions.execute(() -> {
            DonationWallet wallet = walletLocker.lock(userId);
            Charge<T> charge = work.get();
            List<DonationPointLot> lots = lotSpender.reserve(userId, price, LocalDateTime.now(clock));
            lotSpender.spend(wallet, lots, price, DonationLotUsageType.SHOP, charge.purchaseId());
            historyRepository.save(DonationPointHistory.of(wallet, DonationPointHistoryType.SHOP, price, charge.purchaseId()));
            return charge.result();
        });
    }

    /** @param purchaseId 상점 구매 기록 ID (묶음 사용 기록·내역의 refId) */
    public record Charge<T>(Long purchaseId, T result) {
    }
}
