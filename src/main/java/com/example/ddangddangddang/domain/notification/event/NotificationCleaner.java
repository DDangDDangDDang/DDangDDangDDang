package com.example.ddangddangddang.domain.notification.event;

import com.example.ddangddangddang.domain.notification.repository.AuctionClosingNoticeRepository;
import com.example.ddangddangddang.domain.notification.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 오래된 알림을 정리한다. 매일 새벽(기본 04:30)에 한 번 돈다.
 *
 * <pre>
 * notification.cleanup.enabled    사용 여부. 기본 true (테스트 설정에서는 false)
 * notification.cleanup.cron       도는 시각. 기본 매일 04:30
 * notification.cleanup.retention  알림 보관 기간. 기본 90일 (지나면 알림함에서 사라진다)
 * </pre>
 *
 * - 한 번에 지우는 양을 BATCH_SIZE개 정도로 나눠, 큰 삭제로 다른 요청이 오래 기다리지 않게 한다.
 * - 마감 임박 알림 기록(auction_closing_notices)은 경매가 끝나면 쓸 일이 없어 30일이 지나면 지운다.
 * - 서버가 여러 대면 같은 시각에 함께 돌지만, 같은 행을 지우는 것뿐이라 결과는 같다.
 */
@Slf4j
@Component
public class NotificationCleaner {

    static final int BATCH_SIZE = 1000;
    static final Duration CLOSING_NOTICE_RETENTION = Duration.ofDays(30);

    private final NotificationRepository notificationRepository;
    private final AuctionClosingNoticeRepository closingNoticeRepository;
    private final TransactionTemplate transactionTemplate;
    private final boolean enabled;
    private final Duration retention;
    private final Clock clock;

    @Autowired
    public NotificationCleaner(NotificationRepository notificationRepository, AuctionClosingNoticeRepository closingNoticeRepository,
                               TransactionTemplate transactionTemplate,
                               @Value("${notification.cleanup.enabled:true}") boolean enabled,
                               @Value("${notification.cleanup.retention:90d}") Duration retention) {
        this(notificationRepository, closingNoticeRepository, transactionTemplate, enabled, retention, Clock.systemDefaultZone());
    }

    NotificationCleaner(NotificationRepository notificationRepository, AuctionClosingNoticeRepository closingNoticeRepository,
                        TransactionTemplate transactionTemplate, boolean enabled, Duration retention, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.closingNoticeRepository = closingNoticeRepository;
        this.transactionTemplate = transactionTemplate;
        this.enabled = enabled;
        this.retention = retention;
        this.clock = clock;
    }

    @Scheduled(cron = "${notification.cleanup.cron:0 30 4 * * *}")
    public void scheduled() {
        if (enabled) {
            clean();
        }
    }

    /** @return 지운 알림 수 */
    public int clean() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime before = now.minus(retention);
        int deleted = 0;
        try {
            Long maxId = notificationRepository.findMaxIdOlderThan(before);
            Long minId = notificationRepository.findMinId();
            if (maxId != null && minId != null) {
                // id가 작은 쪽부터 BATCH_SIZE씩 올라가며 지운다. (오래된 알림은 id도 작다)
                for (long upTo = minId + BATCH_SIZE - 1; ; upTo += BATCH_SIZE) {
                    long limit = Math.min(upTo, maxId);
                    Integer count = transactionTemplate.execute(status -> notificationRepository.deleteOlderThan(before, limit));
                    deleted += count == null ? 0 : count;
                    if (limit >= maxId) {
                        break;
                    }
                }
            }
            transactionTemplate.executeWithoutResult(status -> closingNoticeRepository.deleteOlderThan(now.minus(CLOSING_NOTICE_RETENTION)));
            if (deleted > 0) {
                log.info("보관 기간({})이 지난 알림 {}건을 지웠습니다.", retention, deleted);
            }
        } catch (RuntimeException e) {
            log.warn("오래된 알림을 정리하지 못했습니다. 다음 날 다시 합니다.", e);
        }
        return deleted;
    }
}
