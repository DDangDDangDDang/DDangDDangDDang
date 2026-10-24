package com.example.ddangddangddang.domain.donation.service;

import com.example.ddangddangddang.domain.donation.policy.DonationPolicies;
import com.example.ddangddangddang.domain.donation.policy.DonationPolicy;
import com.example.ddangddangddang.domain.donation.dto.DonationRequest;
import com.example.ddangddangddang.domain.donation.dto.DonationResponse;
import com.example.ddangddangddang.domain.donation.dto.DonationStatusResponse;
import com.example.ddangddangddang.domain.donation.entity.Donation;
import com.example.ddangddangddang.domain.donation.entity.DonationLotUsage;
import com.example.ddangddangddang.domain.donation.entity.DonationPointHistory;
import com.example.ddangddangddang.domain.donation.entity.DonationPointHistoryType;
import com.example.ddangddangddang.domain.donation.entity.DonationPointLot;
import com.example.ddangddangddang.domain.donation.entity.DonationWallet;
import com.example.ddangddangddang.domain.donation.event.DonationSentEvent;
import com.example.ddangddangddang.domain.donation.exception.DonationErrorCode;
import com.example.ddangddangddang.domain.donation.repository.DonationLotUsageRepository;
import com.example.ddangddangddang.domain.donation.repository.DonationPointHistoryRepository;
import com.example.ddangddangddang.domain.donation.repository.DonationPointLotRepository;
import com.example.ddangddangddang.domain.donation.repository.DonationRepository;
import com.example.ddangddangddang.domain.livechat.moderation.ChatContentFilter;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.exception.LiveStreamErrorCode;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 라이브 방송 중인 판매자에게 도네이션 포인트 선물하기.
 *
 * 규칙
 * - 방송 중(LIVE)인 방송의 판매자에게만, 내 방송이 아닐 때만 선물할 수 있다.
 * - 한 번에 minGift ~ maxGift, giftUnit 단위로 선물한다.
 * - 한 계정이 판매자 한 명에게 한 달(1일~말일)에 선물할 수 있는 포인트에 한도가 있다. (어뷰징 방지)
 * - 유효기간이 먼저 끝나는 적립 묶음부터 쓴다.
 *
 * 동시성
 * - 보낸 사람·판매자 지갑을 userId 순으로 잠근 뒤 한도를 센다. 같은 사람의 선물은 한 줄로 처리되어 한도를 넘지 않는다.
 * - 지갑은 트랜잭션 밖에서 만들어 두고, READ_COMMITTED 트랜잭션에서 잠근다. (DonationWalletLocker, DonationTransactions)
 * - 같은 요청 키(requestKey)로 다시 보내면 처음 결과를 돌려준다. 동시에 같은 키가 오면 잠금 뒤에 다시 확인하고, 마지막으로 유니크 제약이 막는다.
 */
@Service
public class DonationService {

    private final DonationRepository donationRepository;
    private final DonationPointLotRepository lotRepository;
    private final DonationLotUsageRepository usageRepository;
    private final DonationPointHistoryRepository historyRepository;
    private final DonationWalletLocker walletLocker;
    private final LiveStreamRepository liveStreamRepository;
    private final ChatContentFilter contentFilter;
    private final ApplicationEventPublisher eventPublisher;
    private final DonationTransactions transactions;
    private final DonationPolicies policies;
    private final Clock clock;

    @Autowired
    public DonationService(DonationRepository donationRepository, DonationPointLotRepository lotRepository,
                           DonationLotUsageRepository usageRepository, DonationPointHistoryRepository historyRepository,
                           DonationWalletLocker walletLocker,
                           LiveStreamRepository liveStreamRepository, ChatContentFilter contentFilter,
                           ApplicationEventPublisher eventPublisher, DonationTransactions transactions, DonationPolicies policies) {
        this(donationRepository, lotRepository, usageRepository, historyRepository, walletLocker,
                liveStreamRepository, contentFilter, eventPublisher, transactions, policies, Clock.systemDefaultZone());
    }

    DonationService(DonationRepository donationRepository, DonationPointLotRepository lotRepository,
                    DonationLotUsageRepository usageRepository, DonationPointHistoryRepository historyRepository,
                    DonationWalletLocker walletLocker,
                    LiveStreamRepository liveStreamRepository, ChatContentFilter contentFilter,
                    ApplicationEventPublisher eventPublisher, DonationTransactions transactions, DonationPolicies policies, Clock clock) {
        this.donationRepository = donationRepository;
        this.lotRepository = lotRepository;
        this.usageRepository = usageRepository;
        this.historyRepository = historyRepository;
        this.walletLocker = walletLocker;
        this.liveStreamRepository = liveStreamRepository;
        this.contentFilter = contentFilter;
        this.eventPublisher = eventPublisher;
        this.transactions = transactions;
        this.policies = policies;
        this.clock = clock;
    }

    /** 선물 창 정보: 이 방송의 판매자에게 지금 얼마까지 선물할 수 있는지 */
    @Transactional(readOnly = true)
    public DonationStatusResponse getStatus(Long liveStreamId, Long giverId) {
        DonationPolicy policy = policies.current();
        LiveStream liveStream = findLiveStream(liveStreamId);
        Long sellerId = liveStream.getSellerId();
        LocalDateTime now = LocalDateTime.now(clock);
        long given = givenThisMonth(giverId, sellerId, now);
        return new DonationStatusResponse(
                sellerId,
                lotRepository.sumUsable(giverId, now),
                policy.monthlyGiftLimitPerSeller(),
                given,
                Math.max(0, policy.monthlyGiftLimitPerSeller() - given),
                policy.minGift(),
                policy.giftUnit(),
                policy.maxGift()
        );
    }

    /** 선물하기. 커밋 뒤 방송 화면에 "누구님이 몇 P를 선물했어요"를 알린다. (DonationSentEvent) */
    public DonationResponse gift(Long liveStreamId, Long giverId, String giverNickname, DonationRequest request) {
        // 한 선물 안에서는 같은 정책을 쓴다. (도중에 관리자가 바꿔도 섞이지 않게)
        DonationPolicy policy = policies.current();
        long amount = request.amount();
        validateAmount(amount, policy);
        String message = blankToNull(request.message());
        validateMessage(message);
        LiveStream liveStream = findLiveStream(liveStreamId);
        Long sellerId = liveStream.getSellerId();
        if (sellerId.equals(giverId)) {
            throw new BusinessException(DonationErrorCode.SELF_GIFT);
        }
        if (liveStream.getStatus() != LiveStreamStatus.LIVE) {
            throw new BusinessException(DonationErrorCode.NOT_LIVE);
        }
        walletLocker.prepare(giverId, sellerId);
        return transactions.execute(() -> giftLocked(liveStreamId, sellerId, giverId, giverNickname, amount, message, request.requestKey(), policy));
    }

    private DonationResponse giftLocked(Long liveStreamId, Long sellerId, Long giverId, String giverNickname,
                                        long amount, String message, String requestKey, DonationPolicy policy) {
        Map<Long, DonationWallet> wallets = walletLocker.lockBoth(giverId, sellerId);
        DonationWallet giverWallet = wallets.get(giverId);
        DonationWallet sellerWallet = wallets.get(sellerId);
        LocalDateTime now = LocalDateTime.now(clock);

        // 같은 요청을 다시 보냈다. (지갑을 잠근 뒤에 확인해 동시에 온 같은 요청도 한 번만 처리한다)
        Optional<Donation> sent = donationRepository.findByGiverIdAndRequestKey(giverId, requestKey);
        if (sent.isPresent()) {
            Donation donation = sent.get();
            // 같은 키로 다른 선물(금액·방송이 다름)을 보내면 처음 결과를 돌려주지 않고 거부한다. (두 번째 선물이 된 줄 알지 않게)
            if (donation.getAmount() != amount || !donation.getLiveStreamId().equals(liveStreamId)) {
                throw new BusinessException(DonationErrorCode.REQUEST_KEY_REUSED);
            }
            return response(donation, giverWallet, givenThisMonth(giverId, donation.getSellerId(), now), policy);
        }
        // 방송 상태는 잠근 뒤에 다시 본다. (선물하는 사이 방송이 끝났을 수 있음)
        if (findLiveStream(liveStreamId).getStatus() != LiveStreamStatus.LIVE) {
            throw new BusinessException(DonationErrorCode.NOT_LIVE);
        }

        long given = givenThisMonth(giverId, sellerId, now);
        long remaining = policy.monthlyGiftLimitPerSeller() - given;
        if (amount > remaining) {
            throw new BusinessException(DonationErrorCode.MONTHLY_GIFT_LIMIT_EXCEEDED,
                    "이 판매자에게 이번 달 선물할 수 있는 포인트는 %,dP 남았습니다.".formatted(Math.max(0, remaining)));
        }

        List<DonationPointLot> lots = lotRepository.findUsable(giverId, now);
        long usable = lots.stream().mapToLong(DonationPointLot::getRemaining).sum();
        if (usable < amount) {
            throw new BusinessException(DonationErrorCode.INSUFFICIENT_BALANCE);
        }

        Donation donation = donationRepository.saveAndFlush(
                Donation.of(giverId, sellerId, liveStreamId, amount, message, requestKey));
        long left = amount;
        for (DonationPointLot lot : lots) {
            if (left == 0) {
                break;
            }
            long used = lot.use(left);
            if (used > 0) {
                usageRepository.save(new DonationLotUsage(donation.getId(), lot.getId(), used));
                left -= used;
            }
        }
        giverWallet.useGiftable(amount);
        sellerWallet.receive(amount);
        historyRepository.save(DonationPointHistory.of(giverWallet, DonationPointHistoryType.GIFT, amount, donation.getId()));
        historyRepository.save(DonationPointHistory.of(sellerWallet, DonationPointHistoryType.RECEIVE, amount, donation.getId()));

        eventPublisher.publishEvent(new DonationSentEvent(donation.getId(), liveStreamId, giverId, giverNickname, amount, message));
        return response(donation, giverWallet, given + amount, policy);
    }

    private DonationResponse response(Donation donation, DonationWallet giverWallet, long givenThisMonth, DonationPolicy policy) {
        return new DonationResponse(donation.getId(), donation.getAmount(), giverWallet.getGiftableBalance(),
                Math.max(0, policy.monthlyGiftLimitPerSeller() - givenThisMonth));
    }

    private long givenThisMonth(Long giverId, Long sellerId, LocalDateTime now) {
        MonthRange month = MonthRange.of(now);
        return donationRepository.sumGivenBetween(giverId, sellerId, month.from(), month.to());
    }

    private void validateAmount(long amount, DonationPolicy policy) {
        if (amount < policy.minGift() || amount > policy.maxGift() || amount % policy.giftUnit() != 0) {
            throw new BusinessException(DonationErrorCode.INVALID_GIFT_AMOUNT,
                    "선물은 %,dP부터 %,dP까지 %,dP 단위로 할 수 있습니다.".formatted(policy.minGift(), policy.maxGift(), policy.giftUnit()));
        }
    }

    // 선물 메시지도 방송 화면에 보이므로 채팅과 같은 규칙으로 연락처·외부 링크를 막는다.
    private void validateMessage(String message) {
        contentFilter.findViolation(message).ifPresent(violation -> {
            throw new BusinessException(DonationErrorCode.MESSAGE_NOT_ALLOWED, violation.getMessage());
        });
    }

    private LiveStream findLiveStream(Long liveStreamId) {
        return liveStreamRepository.findById(liveStreamId)
                .orElseThrow(() -> new BusinessException(LiveStreamErrorCode.NOT_FOUND));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
