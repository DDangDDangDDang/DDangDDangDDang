package com.example.ddangddangddang.domain.donation.service;

import com.example.ddangddangddang.domain.donation.policy.DonationPolicies;
import com.example.ddangddangddang.domain.donation.policy.DonationPolicy;
import com.example.ddangddangddang.domain.donation.entity.DonationPointHistory;
import com.example.ddangddangddang.domain.donation.entity.DonationPointHistoryType;
import com.example.ddangddangddang.domain.donation.entity.DonationPointLot;
import com.example.ddangddangddang.domain.donation.entity.DonationWallet;
import com.example.ddangddangddang.domain.donation.repository.DonationPointHistoryRepository;
import com.example.ddangddangddang.domain.donation.repository.DonationPointLotRepository;
import com.example.ddangddangddang.domain.notification.entity.Notification;
import com.example.ddangddangddang.domain.notification.entity.NotificationType;
import com.example.ddangddangddang.domain.notification.service.NotificationDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 매일 새벽 도네이션 포인트 유효기간을 정리한다.
 * 1. 유효기간이 지난 적립 묶음의 남은 포인트를 없애고(EXPIRE) 지갑에서 뺀다. 회원 한 명씩 지갑을 잠그고 따로 된 트랜잭션에서 한다.
 *    (한 회원이 실패해도 나머지는 정리하고, 실패한 회원은 다음 날 다시 한다)
 * 2. 유효기간이 곧(기본 7일 안에) 끝나는 묶음이 있는 회원에게 한 번 알린다. "사라지기 전에 선물해 보세요" (선물을 미루지 않게)
 *
 * 서버가 여러 대면 같은 시각에 함께 돌 수 있다. 만료는 지갑을 잠그고 남은 양이 있는 묶음만 처리하므로 두 번 빠지지 않는다.
 * 알림은 드물게 두 번 갈 수 있다. (NotificationCleaner와 같은 방식)
 */
@Slf4j
@Component
public class DonationPointExpirer {

    static final int BATCH_SIZE = 500;

    private final DonationPointLotRepository lotRepository;
    private final DonationPointHistoryRepository historyRepository;
    private final DonationWalletLocker walletLocker;
    private final NotificationDispatcher notificationDispatcher;
    private final TransactionTemplate transactionTemplate;
    private final DonationPolicies policies;
    private final boolean enabled;
    private final Clock clock;

    @Autowired
    public DonationPointExpirer(DonationPointLotRepository lotRepository, DonationPointHistoryRepository historyRepository,
                                DonationWalletLocker walletLocker, NotificationDispatcher notificationDispatcher,
                                TransactionTemplate transactionTemplate, DonationPolicies policies,
                                @Value("${donation.expiry.enabled:true}") boolean enabled) {
        this(lotRepository, historyRepository, walletLocker, notificationDispatcher, transactionTemplate, policies, enabled,
                Clock.systemDefaultZone());
    }

    DonationPointExpirer(DonationPointLotRepository lotRepository, DonationPointHistoryRepository historyRepository,
                         DonationWalletLocker walletLocker, NotificationDispatcher notificationDispatcher,
                         TransactionTemplate transactionTemplate, DonationPolicies policies, boolean enabled, Clock clock) {
        this.lotRepository = lotRepository;
        this.historyRepository = historyRepository;
        this.walletLocker = walletLocker;
        this.notificationDispatcher = notificationDispatcher;
        // 회원마다 새 트랜잭션. 잠금 뒤 다른 트랜잭션이 끝낸 선물을 보도록 READ_COMMITTED (DonationService와 같은 이유)
        this.transactionTemplate = new TransactionTemplate(transactionTemplate.getTransactionManager());
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.policies = policies;
        this.enabled = enabled;
        this.clock = clock;
    }

    @Scheduled(cron = "${donation.expiry.cron:0 10 4 * * *}")
    public void scheduled() {
        if (enabled) {
            expire();
            notifyExpiring();
        }
    }

    /** @return 없앤 포인트 합계 */
    public long expire() {
        LocalDateTime now = LocalDateTime.now(clock);
        long total = 0;
        // userId 순으로 넘긴다. 실패한 회원은 건너뛰고 다음 날 다시 한다.
        Long after = 0L;
        while (true) {
            List<Long> userIds = lotRepository.findUserIdsWithExpired(now, after, PageRequest.of(0, BATCH_SIZE));
            for (Long userId : userIds) {
                try {
                    Long expired = transactionTemplate.execute(status -> expireFor(userId, now));
                    total += expired == null ? 0 : expired;
                } catch (RuntimeException e) {
                    log.warn("도네이션 포인트 유효기간을 정리하지 못했습니다. 다음 날 다시 합니다. userId={}", userId, e);
                }
            }
            if (userIds.size() < BATCH_SIZE) {
                break;
            }
            after = userIds.get(userIds.size() - 1);
        }
        if (total > 0) {
            log.info("유효기간이 지난 도네이션 포인트 {}P를 정리했습니다.", total);
        }
        return total;
    }

    private long expireFor(Long userId, LocalDateTime now) {
        DonationWallet wallet = walletLocker.lock(userId);
        long total = 0;
        for (DonationPointLot lot : lotRepository.findExpired(userId, now)) {
            long expired = lot.expire();
            if (expired <= 0) {
                continue;
            }
            // 지갑 잔액은 묶음 합계의 캐시다. 혹시 어긋나 있어도 음수가 되지 않게 남은 만큼만 뺀다.
            long deducted = Math.min(expired, wallet.getBalance());
            if (deducted > 0) {
                wallet.deduct(deducted);
            }
            historyRepository.save(DonationPointHistory.of(wallet, DonationPointHistoryType.EXPIRE, expired, lot.getId()));
            total += expired;
        }
        return total;
    }

    /** 곧 사라질 포인트가 있는 회원에게 알린다. @return 알린 회원 수 */
    public int notifyExpiring() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime until = now.plus(policies.current().expiringNoticeBefore());
        int notified = 0;
        try {
            // 회원 단위로 넘긴다. 한 회원의 묶음이 여러 페이지로 나뉘어 알림이 두 번 가지 않게, 회원을 고른 뒤 그 회원의 묶음을 모두 합친다.
            Long afterUserId = 0L;
            while (true) {
                List<Long> userIds = lotRepository.findUserIdsToNotify(now, until, afterUserId, PageRequest.of(0, BATCH_SIZE));
                if (userIds.isEmpty()) {
                    break;
                }
                List<DonationPointLot> lots = lotRepository.findToNotify(userIds, now, until);
                Map<Long, Long> pointsByUser = new LinkedHashMap<>();
                lots.forEach(lot -> pointsByUser.merge(lot.getUserId(), lot.getRemaining(), Long::sum));
                // 알림을 저장한 뒤에 알린 묶음으로 표시한다. 저장이 실패하면(예외) 표시하지 않고 멈춰 다음 날 다시 알린다.
                notificationDispatcher.dispatch(pointsByUser.entrySet().stream()
                        .map(e -> Notification.donationPoint(e.getKey(), NotificationType.DONATION_POINT_EXPIRING, e.getValue()))
                        .toList());
                List<Long> lotIds = lots.stream().map(DonationPointLot::getId).toList();
                transactionTemplate.executeWithoutResult(status -> lotRepository.markExpiryNotified(lotIds));
                notified += pointsByUser.size();
                if (userIds.size() < BATCH_SIZE) {
                    break;
                }
                afterUserId = userIds.get(userIds.size() - 1);
            }
        } catch (RuntimeException e) {
            log.warn("곧 사라질 도네이션 포인트를 알리지 못했습니다. 다음 날 다시 합니다.", e);
        }
        return notified;
    }
}
