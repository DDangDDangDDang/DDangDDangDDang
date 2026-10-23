package com.example.ddangddangddang.domain.donation.service;

import com.example.ddangddangddang.domain.donation.config.DonationProperties;
import com.example.ddangddangddang.domain.donation.dto.DonationPointHistoryResponse;
import com.example.ddangddangddang.domain.donation.dto.DonationWalletResponse;
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
 * 도네이션 포인트 지갑·내역 조회와 구매 확정 적립.
 *
 * 적립: 구매 확정한 주문 한 건마다 낙찰가의 일정 비율(최대값 있음)을 적립 묶음 하나로 쌓는다.
 * 한 달 적립 한도를 넘는 만큼은 적립하지 않는다. 같은 주문으로는 한 번만 적립한다. (적립 묶음의 order_id 유니크)
 */
@Service
public class DonationPointService {

    private static final int MAX_PAGE_SIZE = 100;

    private final DonationWalletRepository walletRepository;
    private final DonationWalletLocker walletLocker;
    private final DonationPointLotRepository lotRepository;
    private final DonationPointHistoryRepository historyRepository;
    private final DonationTransactions transactions;
    private final DonationProperties properties;
    private final Clock clock;

    @Autowired
    public DonationPointService(DonationWalletRepository walletRepository, DonationWalletLocker walletLocker,
                                DonationPointLotRepository lotRepository, DonationPointHistoryRepository historyRepository,
                                DonationTransactions transactions, DonationProperties properties) {
        this(walletRepository, walletLocker, lotRepository, historyRepository, transactions, properties, Clock.systemDefaultZone());
    }

    DonationPointService(DonationWalletRepository walletRepository, DonationWalletLocker walletLocker,
                         DonationPointLotRepository lotRepository, DonationPointHistoryRepository historyRepository,
                         DonationTransactions transactions, DonationProperties properties, Clock clock) {
        this.walletRepository = walletRepository;
        this.walletLocker = walletLocker;
        this.lotRepository = lotRepository;
        this.historyRepository = historyRepository;
        this.transactions = transactions;
        this.properties = properties;
        this.clock = clock;
    }

    /** 내 도네이션 포인트. 지갑이 아직 없으면(적립·선물한 적 없음) 0으로 보여 준다. */
    @Transactional(readOnly = true)
    public DonationWalletResponse getWallet(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        DonationWallet wallet = walletRepository.findByUserId(userId).orElse(null);
        MonthRange month = MonthRange.of(now);
        return new DonationWalletResponse(
                wallet == null ? 0 : wallet.getGiftableBalance(),
                wallet == null ? 0 : wallet.getReceivedBalance(),
                lotRepository.sumExpiringBetween(userId, now, now.plus(properties.expiringNoticeBefore())),
                lotRepository.findNextExpiry(userId, now),
                lotRepository.sumEarnedBetween(userId, month.from(), month.to()),
                properties.monthlyEarnLimit()
        );
    }

    @Transactional(readOnly = true)
    public CursorResponse<DonationPointHistoryResponse> getHistories(Long userId, Long cursor, int size) {
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        List<DonationPointHistory> fetched = historyRepository.findByCursor(userId, cursor, PageRequest.of(0, pageSize + 1));
        return CursorResponse.of(fetched, pageSize, DonationPointHistoryResponse::from, DonationPointHistory::getId);
    }

    /**
     * 구매 확정 적립. 구매자 지갑을 잠근 채 이번 달 적립 합계를 세어, 한도 안에서만 적립한다.
     * 지갑을 먼저 만들어 두고 트랜잭션을 연다. (DonationWalletLocker, DonationTransactions)
     *
     * @return 적립한 포인트. 이미 적립한 주문이거나 한도를 다 썼거나 적립할 금액이 없으면 0
     */
    public long earn(Long orderId, Long buyerId, Long sellerId, long finalPrice) {
        long wanted = properties.earnFor(finalPrice);
        if (wanted <= 0) {
            return 0;
        }
        walletLocker.prepare(buyerId);
        return transactions.execute(() -> earnLocked(orderId, buyerId, sellerId, wanted));
    }

    private long earnLocked(Long orderId, Long buyerId, Long sellerId, long wanted) {
        LocalDateTime now = LocalDateTime.now(clock);
        DonationWallet wallet = walletLocker.lock(buyerId);
        // 같은 주문은 한 번만 (지갑을 잠근 뒤에 확인해 동시에 온 적립도 한 번만. 마지막으로 order_id 유니크가 막는다)
        if (lotRepository.existsByOrderId(orderId)) {
            return 0;
        }
        MonthRange month = MonthRange.of(now);
        long earnedThisMonth = lotRepository.sumEarnedBetween(buyerId, month.from(), month.to());
        long amount = Math.min(wanted, properties.monthlyEarnLimit() - earnedThisMonth);
        if (amount <= 0) {
            return 0;
        }
        lotRepository.save(DonationPointLot.earn(buyerId, orderId, sellerId, amount, now, now.plus(properties.pointValidFor())));
        wallet.earn(amount);
        historyRepository.save(DonationPointHistory.of(wallet, DonationPointHistoryType.EARN, amount, orderId));
        return amount;
    }
}
