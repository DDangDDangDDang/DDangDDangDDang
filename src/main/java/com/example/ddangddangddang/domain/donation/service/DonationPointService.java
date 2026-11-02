package com.example.ddangddangddang.domain.donation.service;

import com.example.ddangddangddang.domain.donation.policy.DonationPolicies;
import com.example.ddangddangddang.domain.donation.policy.DonationPolicy;
import com.example.ddangddangddang.domain.donation.dto.DonationPointHistoryResponse;
import com.example.ddangddangddang.domain.donation.dto.DonationWalletResponse;
import com.example.ddangddangddang.domain.donation.entity.DonationLotSource;
import com.example.ddangddangddang.domain.donation.entity.DonationPointHistory;
import com.example.ddangddangddang.domain.donation.entity.DonationPointHistoryType;
import com.example.ddangddangddang.domain.donation.entity.DonationPointLot;
import com.example.ddangddangddang.domain.donation.entity.DonationWallet;
import com.example.ddangddangddang.domain.donation.repository.DonationPointHistoryRepository;
import com.example.ddangddangddang.domain.donation.repository.DonationPointLotRepository;
import com.example.ddangddangddang.domain.donation.repository.DonationWalletRepository;
import com.example.ddangddangddang.global.common.CursorResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 도네이션 마일리지 지갑·내역 조회와 구매 확정 적립.
 *
 * 적립: 구매 확정한 주문 한 건마다 구매자와 판매자에게 각각 낙찰가의 일정 비율(최대값 있음)을 적립 묶음 하나로 쌓는다.
 * 비율·주문당 최대·한 달 한도는 같은 정책 값을 쓰고, 한 달 한도는 구매 적립과 판매 적립을 따로 센다.
 * 한 달 적립 한도를 넘는 만큼은 적립하지 않는다. 같은 주문으로는 구매자·판매자에게 한 번씩만 적립한다. (적립 묶음의 (order_id, source) 유니크)
 */
@Service
public class DonationPointService {

    private static final int MAX_PAGE_SIZE = 100;

    private final DonationWalletRepository walletRepository;
    private final DonationWalletLocker walletLocker;
    private final DonationPointLotRepository lotRepository;
    private final DonationPointHistoryRepository historyRepository;
    private final DonationTransactions transactions;
    private final DonationPolicies policies;
    private final Clock clock;

    @Autowired
    public DonationPointService(DonationWalletRepository walletRepository, DonationWalletLocker walletLocker,
                                DonationPointLotRepository lotRepository, DonationPointHistoryRepository historyRepository,
                                DonationTransactions transactions, DonationPolicies policies) {
        this(walletRepository, walletLocker, lotRepository, historyRepository, transactions, policies, Clock.systemDefaultZone());
    }

    DonationPointService(DonationWalletRepository walletRepository, DonationWalletLocker walletLocker,
                         DonationPointLotRepository lotRepository, DonationPointHistoryRepository historyRepository,
                         DonationTransactions transactions, DonationPolicies policies, Clock clock) {
        this.walletRepository = walletRepository;
        this.walletLocker = walletLocker;
        this.lotRepository = lotRepository;
        this.historyRepository = historyRepository;
        this.transactions = transactions;
        this.policies = policies;
        this.clock = clock;
    }

    /** 내 도네이션 마일리지. 지갑이 아직 없으면(적립·선물한 적 없음) 0으로 보여 준다. */
    @Transactional(readOnly = true)
    public DonationWalletResponse getWallet(Long userId) {
        DonationPolicy policy = policies.current();
        LocalDateTime now = LocalDateTime.now(clock);
        DonationWallet wallet = walletRepository.findByUserId(userId).orElse(null);
        MonthRange month = MonthRange.of(now);
        return new DonationWalletResponse(
                wallet == null ? 0 : wallet.getBalance(),
                lotRepository.sumExpiringBetween(userId, now, now.plus(policy.expiringNoticeBefore())),
                lotRepository.findNextExpiry(userId, now),
                lotRepository.sumEarnedBetween(userId, DonationLotSource.PURCHASE, month.from(), month.to()),
                lotRepository.sumEarnedBetween(userId, DonationLotSource.SALE, month.from(), month.to()),
                policy.monthlyEarnLimit()
        );
    }

    @Transactional(readOnly = true)
    public CursorResponse<DonationPointHistoryResponse> getHistories(Long userId, Long cursor, int size) {
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        List<DonationPointHistory> fetched = historyRepository.findByCursor(userId, cursor, PageRequest.of(0, pageSize + 1));
        return CursorResponse.of(fetched, pageSize, DonationPointHistoryResponse::from, DonationPointHistory::getId);
    }

    /**
     * 구매 확정 적립 (구매자). 구매자 지갑을 잠근 채 이번 달 구매 적립 합계를 세어, 한도 안에서만 적립한다.
     * 지갑을 먼저 만들어 두고 트랜잭션을 연다. (DonationWalletLocker, DonationTransactions)
     *
     * @return 적립한 마일리지. 이미 적립한 주문이거나 한도를 다 썼거나 적립할 금액이 없으면 0
     */
    public long earn(Long orderId, Long buyerId, Long sellerId, long finalPrice) {
        return earn(DonationLotSource.PURCHASE, orderId, buyerId, sellerId, finalPrice);
    }

    /**
     * 판매 확정 적립 (판매자). 구매 확정된 주문의 판매자에게 구매 적립과 같은 비율로 적립한다. 한 달 한도는 판매 적립끼리 센다.
     *
     * @return 적립한 마일리지. 이미 적립한 주문이거나 한도를 다 썼거나 적립할 금액이 없으면 0
     */
    public long earnSale(Long orderId, Long sellerId, long finalPrice) {
        return earn(DonationLotSource.SALE, orderId, sellerId, sellerId, finalPrice);
    }

    private long earn(DonationLotSource source, Long orderId, Long userId, Long sellerId, long finalPrice) {
        DonationPolicy policy = policies.current();
        walletLocker.prepare(userId);
        return transactions.execute(() -> earnLocked(source, orderId, userId, sellerId, finalPrice, policy));
    }

    private long earnLocked(DonationLotSource source, Long orderId, Long userId, Long sellerId, long finalPrice, DonationPolicy policy) {
        long wanted = policy.earnFor(finalPrice);
        LocalDateTime now = LocalDateTime.now(clock);
        DonationWallet wallet = walletLocker.lock(userId);
        // 같은 주문은 한 번만 (지갑을 잠근 뒤에 확인해 동시에 온 적립도 한 번만. 마지막으로 (order_id, source) 유니크가 막는다)
        if (lotRepository.existsByOrderIdAndSource(orderId, source)) {
            return 0;
        }
        MonthRange month = MonthRange.of(now);
        long earnedThisMonth = lotRepository.sumEarnedBetween(userId, source, month.from(), month.to());
        long amount = Math.max(0, Math.min(wanted, policy.monthlyEarnLimit() - earnedThisMonth));
        if (amount == 0) {
            // 한도를 다 썼거나 적립할 마일리지가 없는(낙찰가가 아주 작은) 주문도 0M 묶음으로 남긴다. 적립 보정(DonationPointEarner.backfill)이 나중에 다시 적립하지 않게 한다.
            lotRepository.save(lot(source, orderId, userId, sellerId, 0, now, now));
            return 0;
        }
        lotRepository.save(lot(source, orderId, userId, sellerId, amount, now, now.plus(policy.pointValidFor())));
        wallet.add(amount);
        DonationPointHistoryType historyType = source == DonationLotSource.SALE ? DonationPointHistoryType.SALE : DonationPointHistoryType.EARN;
        historyRepository.save(DonationPointHistory.of(wallet, historyType, amount, orderId));
        return amount;
    }

    private static DonationPointLot lot(DonationLotSource source, Long orderId, Long userId, Long sellerId, long amount,
                                        LocalDateTime earnedAt, LocalDateTime expiresAt) {
        return source == DonationLotSource.SALE
                ? DonationPointLot.sale(userId, orderId, amount, earnedAt, expiresAt)
                : DonationPointLot.purchase(userId, orderId, sellerId, amount, earnedAt, expiresAt);
    }
}
