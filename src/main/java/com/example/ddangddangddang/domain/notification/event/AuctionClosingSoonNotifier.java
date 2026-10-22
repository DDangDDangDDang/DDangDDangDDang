package com.example.ddangddangddang.domain.notification.event;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.notification.entity.AuctionClosingNotice;
import com.example.ddangddangddang.domain.notification.entity.NotificationType;
import com.example.ddangddangddang.domain.notification.repository.AuctionClosingNoticeRepository;
import com.example.ddangddangddang.domain.notification.service.NotificationDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 진행 중인 경매가 마감 10분 전이 되면 알림 받기·입찰한 회원에게 알린다. (경매마다 한 번)
 *
 * - 주기(기본 30초)마다 종료 시각이 지금~10분 뒤인 진행 중 경매를 찾는다. 10분보다 짧게 연 경매는 시작하자마자 알린다.
 * - 보낸 경매는 auction_closing_notices에 남겨 다시 보내지 않는다. 마감 직전 입찰로 종료 시각이 늘어나도 그대로 한 번이다.
 * - 서버가 여러 대여도 기록(기본키) 저장에 성공한 서버만 보낸다.
 */
@Slf4j
@Component
public class AuctionClosingSoonNotifier {

    static final Duration BEFORE_CLOSE = Duration.ofMinutes(10);

    private final AuctionRepository auctionRepository;
    private final AuctionClosingNoticeRepository noticeRepository;
    private final AuctionNotifier auctionNotifier;
    private final NotificationDispatcher notificationDispatcher;
    private final TransactionTemplate transactionTemplate;
    private final boolean enabled;
    private final Clock clock;

    @Autowired
    public AuctionClosingSoonNotifier(AuctionRepository auctionRepository, AuctionClosingNoticeRepository noticeRepository,
                                      AuctionNotifier auctionNotifier, NotificationDispatcher notificationDispatcher,
                                      TransactionTemplate transactionTemplate,
                                      @Value("${notification.closing-soon.enabled:true}") boolean enabled) {
        this(auctionRepository, noticeRepository, auctionNotifier, notificationDispatcher, transactionTemplate, enabled, Clock.systemDefaultZone());
    }

    AuctionClosingSoonNotifier(AuctionRepository auctionRepository, AuctionClosingNoticeRepository noticeRepository,
                               AuctionNotifier auctionNotifier, NotificationDispatcher notificationDispatcher,
                               TransactionTemplate transactionTemplate, boolean enabled, Clock clock) {
        this.auctionRepository = auctionRepository;
        this.noticeRepository = noticeRepository;
        this.auctionNotifier = auctionNotifier;
        this.notificationDispatcher = notificationDispatcher;
        this.transactionTemplate = transactionTemplate;
        this.enabled = enabled;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${notification.closing-soon.interval-ms:30000}")
    public void scheduled() {
        if (enabled) {
            notifyClosingSoon();
        }
    }

    public void notifyClosingSoon() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Target> targets = transactionTemplate.execute(status ->
                auctionRepository.findByStatusAndEndAtBetween(AuctionStatus.ONGOING, now, now.plus(BEFORE_CLOSE)).stream()
                        .map(Target::of)
                        .toList());
        if (targets == null) {
            return;
        }
        for (Target target : targets) {
            if (!claim(target.auctionId(), now)) {
                continue;
            }
            try {
                notificationDispatcher.dispatch(AuctionNotifier.notifications(auctionNotifier.watchers(target.auctionId()),
                        NotificationType.AUCTION_CLOSING_SOON, target.auctionId(), target.productName(), null));
            } catch (RuntimeException e) {
                // 알림 저장이 실패했으면(대상 조회·저장 오류) 기록을 지워 다음 주기에 다시 보낸다.
                // 저장 뒤의 실시간·웹 푸시 실패는 dispatch가 삼키므로 여기로 오지 않아 중복 저장되지 않는다.
                log.warn("마감 임박 알림을 보내지 못해 다음 주기에 다시 보냅니다. auctionId={}", target.auctionId(), e);
                release(target.auctionId());
            }
        }
    }

    /** 이 경매의 마감 임박 알림을 이 서버가 맡는다. 이미 보냈으면 false */
    private boolean claim(Long auctionId, LocalDateTime now) {
        if (noticeRepository.existsById(auctionId)) {
            return false;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> noticeRepository.saveAndFlush(new AuctionClosingNotice(auctionId, now)));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    private void release(Long auctionId) {
        try {
            noticeRepository.deleteById(auctionId);
        } catch (RuntimeException e) {
            log.warn("마감 임박 알림 기록을 지우지 못했습니다. auctionId={}", auctionId, e);
        }
    }

    private record Target(Long auctionId, String productName) {
        static Target of(Auction auction) {
            return new Target(auction.getId(), auction.getProduct().getName());
        }
    }
}
