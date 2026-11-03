package com.example.ddangddangddang.domain.donation.event;

import com.example.ddangddangddang.domain.donation.repository.DonationPointLotRepository;
import com.example.ddangddangddang.domain.donation.service.DonationPointService;
import com.example.ddangddangddang.domain.notification.entity.Notification;
import com.example.ddangddangddang.domain.notification.entity.NotificationType;
import com.example.ddangddangddang.domain.notification.service.NotificationDispatcher;
import com.example.ddangddangddang.domain.seller.entity.SellerApplication;
import com.example.ddangddangddang.domain.seller.event.SellerApprovedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 판매자 모집 이벤트: 판매자 신청이 승인되면 도네이션 마일리지를 한 번 지급하고 알린다. (회원 한 명에게 한 번만)
 * 지급량은 donation.seller-welcome.amount (기본 5,000M). 이벤트가 끝나면 0으로 두면 더 지급하지 않는다.
 *
 * 1. 승인이 커밋되면 따로 된 스레드(@Async)·트랜잭션에서 지급한다. 실패해도 승인은 이미 끝났으므로 기록만 남긴다.
 * 2. 실패한 지급은 매일 새벽 보정한다. 최근 BACKFILL_WINDOW 동안 승인됐는데 축하 묶음이 없는 신청을 찾아 다시 지급한다.
 *    방금 승인해 이벤트 처리 중인 신청과 겹치지 않도록 BACKFILL_DELAY보다 오래된 승인만 본다. (겹쳐도 지갑 잠금 뒤 확인해 한 번만 준다)
 */
@Slf4j
@Component
public class SellerWelcomeRewarder {

    static final Duration BACKFILL_WINDOW = Duration.ofDays(7);
    static final Duration BACKFILL_DELAY = Duration.ofMinutes(10);
    static final int BACKFILL_BATCH_SIZE = 200;

    private final DonationPointService donationPointService;
    private final DonationPointLotRepository lotRepository;
    private final NotificationDispatcher notificationDispatcher;
    private final long amount;
    private final boolean backfillEnabled;

    public SellerWelcomeRewarder(DonationPointService donationPointService, DonationPointLotRepository lotRepository,
                                 NotificationDispatcher notificationDispatcher,
                                 @Value("${donation.seller-welcome.amount:5000}") long amount,
                                 @Value("${donation.backfill.enabled:true}") boolean backfillEnabled) {
        this.donationPointService = donationPointService;
        this.lotRepository = lotRepository;
        this.notificationDispatcher = notificationDispatcher;
        this.amount = amount;
        this.backfillEnabled = backfillEnabled;
    }

    @Async
    @TransactionalEventListener
    public void handleSellerApproved(SellerApprovedEvent event) {
        grant(event.applicationId(), event.userId());
    }

    @Scheduled(cron = "${donation.seller-welcome.backfill-cron:0 30 4 * * *}")
    public void scheduled() {
        if (backfillEnabled && amount > 0) {
            backfill();
        }
    }

    /** 지급하지 못한 최근 승인을 다시 지급한다. @return 다시 처리한 신청 수 */
    public int backfill() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.minus(BACKFILL_WINDOW);
        LocalDateTime to = now.minus(BACKFILL_DELAY);
        int processed = 0;
        try {
            // 신청 ID 순으로 넘긴다. 실패한 신청은 이번에는 건너뛰고 다음 날 다시 한다.
            Long afterId = 0L;
            while (true) {
                List<SellerApplication> applications = lotRepository.findApprovedWithoutWelcomeLot(from, to, afterId, PageRequest.of(0, BACKFILL_BATCH_SIZE));
                for (SellerApplication application : applications) {
                    if (grant(application.getId(), application.getUserId())) {
                        processed++;
                    }
                }
                if (applications.size() < BACKFILL_BATCH_SIZE) {
                    break;
                }
                afterId = applications.get(applications.size() - 1).getId();
            }
        } catch (RuntimeException e) {
            log.warn("판매자 승인 축하 마일리지 보정을 끝내지 못했습니다. 다음 날 다시 합니다.", e);
        }
        if (processed > 0) {
            log.info("지급하지 못했던 판매자 승인 {}건을 다시 처리했습니다.", processed);
        }
        return processed;
    }

    /** @return 처리했으면 true (지급했거나 이미 받음·이벤트 끝남), 실패했으면 false */
    private boolean grant(Long applicationId, Long userId) {
        long points;
        try {
            points = donationPointService.grantSellerWelcome(userId, applicationId, amount);
        } catch (RuntimeException e) {
            log.warn("판매자 승인 축하 마일리지를 지급하지 못했습니다. 새벽 보정에서 다시 합니다. applicationId={}", applicationId, e);
            return false;
        }
        // 지급은 끝났으므로 알림이 실패해도 지급 실패로 보지 않는다.
        if (points > 0) {
            try {
                notificationDispatcher.dispatch(List.of(
                        Notification.donationPoint(userId, NotificationType.DONATION_POINT_SELLER_WELCOME, points)));
            } catch (RuntimeException e) {
                log.warn("판매자 승인 축하 마일리지 알림을 보내지 못했습니다. applicationId={}", applicationId, e);
            }
        }
        return true;
    }
}
