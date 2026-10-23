package com.example.ddangddangddang.domain.donation.service;

import com.example.ddangddangddang.domain.donation.policy.DonationPolicies;
import com.example.ddangddangddang.domain.donation.policy.DonationPolicy;
import com.example.ddangddangddang.domain.donation.dto.SellerPerkResponse;
import com.example.ddangddangddang.domain.donation.dto.SellerShopItemResponse;
import com.example.ddangddangddang.domain.donation.entity.DonationPointHistory;
import com.example.ddangddangddang.domain.donation.entity.DonationPointHistoryType;
import com.example.ddangddangddang.domain.donation.entity.DonationWallet;
import com.example.ddangddangddang.domain.donation.entity.SellerPerk;
import com.example.ddangddangddang.domain.donation.entity.SellerPerkType;
import com.example.ddangddangddang.domain.donation.exception.DonationErrorCode;
import com.example.ddangddangddang.domain.donation.repository.DonationPointHistoryRepository;
import com.example.ddangddangddang.domain.donation.repository.SellerPerkRepository;
import com.example.ddangddangddang.domain.seller.service.SellerVerification;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 판매자 상점: 선물받은 도네이션 포인트로 혜택을 산다. 지금은 파워 판매자 하나.
 * 파워 판매자: 기간(기본 30일) 동안 라이브 목록 맨 위에 방송이 올라가고, 프로필·방송 카드에 파워 판매자 배지가 붙는다.
 * 진행 중일 때 또 사면 끝나는 시각부터 이어 붙인다.
 */
@Service
public class SellerPerkService {

    private final SellerPerkRepository perkRepository;
    private final DonationPointHistoryRepository historyRepository;
    private final DonationWalletLocker walletLocker;
    private final SellerVerification sellerVerification;
    private final DonationTransactions transactions;
    private final DonationPolicies policies;
    private final Clock clock;

    @Autowired
    public SellerPerkService(SellerPerkRepository perkRepository, DonationPointHistoryRepository historyRepository,
                             DonationWalletLocker walletLocker, SellerVerification sellerVerification, DonationTransactions transactions,
                             DonationPolicies policies) {
        this(perkRepository, historyRepository, walletLocker, sellerVerification, transactions, policies, Clock.systemDefaultZone());
    }

    SellerPerkService(SellerPerkRepository perkRepository, DonationPointHistoryRepository historyRepository,
                      DonationWalletLocker walletLocker, SellerVerification sellerVerification, DonationTransactions transactions,
                      DonationPolicies policies, Clock clock) {
        this.perkRepository = perkRepository;
        this.historyRepository = historyRepository;
        this.walletLocker = walletLocker;
        this.sellerVerification = sellerVerification;
        this.transactions = transactions;
        this.policies = policies;
        this.clock = clock;
    }

    /** 판매자 상점 상품 */
    public List<SellerShopItemResponse> getShop() {
        DonationPolicy policy = policies.current();
        return List.of(new SellerShopItemResponse(
                SellerPerkType.POWER_SELLER,
                "파워 판매자",
                "%d일 동안 라이브 목록 맨 위에 방송이 올라가고, 프로필과 방송 카드에 파워 판매자 배지가 붙어요."
                        .formatted(policy.powerSellerDays()),
                policy.powerSellerPrice(),
                policy.powerSellerDays()
        ));
    }

    /** 아직 끝나지 않은 내 혜택 (진행 중 + 이어 붙여 기다리는 것) */
    @Transactional(readOnly = true)
    public List<SellerPerkResponse> getMine(Long sellerId) {
        return perkRepository.findBySellerIdAndEndsAtAfterOrderByStartsAtAsc(sellerId, LocalDateTime.now(clock)).stream()
                .map(SellerPerkResponse::from)
                .toList();
    }

    /** 혜택을 산다. 판매자 지갑을 잠그고 받은 포인트에서 뺀다. */
    public SellerPerkResponse purchase(Long sellerId, SellerPerkType type) {
        if (!sellerVerification.isVerified(sellerId)) {
            throw new BusinessException(DonationErrorCode.NOT_VERIFIED_SELLER);
        }
        walletLocker.prepare(sellerId);
        DonationPolicy policy = policies.current();
        return transactions.execute(() -> purchaseLocked(sellerId, type, policy));
    }

    private SellerPerkResponse purchaseLocked(Long sellerId, SellerPerkType type, DonationPolicy policy) {
        long price = price(type, policy);
        Duration period = period(type, policy);
        DonationWallet wallet = walletLocker.lock(sellerId);
        wallet.spendReceived(price);

        // DB는 마이크로초까지만 저장하므로 초 단위로 맞춘다. (이어 붙인 혜택의 시작 = 앞 혜택의 끝이 응답에서도 같게)
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        // 진행 중이거나 기다리는 같은 혜택이 있으면 그 끝에 이어 붙인다. (지갑을 잠근 뒤에 읽어 동시에 사도 겹치지 않음)
        LocalDateTime startsAt = perkRepository.findTopBySellerIdAndTypeOrderByEndsAtDesc(sellerId, type)
                .map(SellerPerk::getEndsAt)
                .filter(endsAt -> endsAt.isAfter(now))
                .orElse(now);
        SellerPerk perk = perkRepository.save(SellerPerk.of(sellerId, type, price, startsAt, startsAt.plus(period)));
        historyRepository.save(DonationPointHistory.of(wallet, DonationPointHistoryType.SPEND, price, perk.getId()));
        return SellerPerkResponse.from(perk);
    }

    private long price(SellerPerkType type, DonationPolicy policy) {
        return switch (type) {
            case POWER_SELLER -> policy.powerSellerPrice();
        };
    }

    private Duration period(SellerPerkType type, DonationPolicy policy) {
        return switch (type) {
            case POWER_SELLER -> policy.powerSellerPeriod();
        };
    }
}
