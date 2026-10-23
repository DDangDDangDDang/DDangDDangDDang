package com.example.ddangddangddang.domain.notification.event;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.notification.entity.AuctionClosingNotice;
import com.example.ddangddangddang.domain.notification.entity.Notification;
import com.example.ddangddangddang.domain.notification.entity.NotificationType;
import com.example.ddangddangddang.domain.notification.repository.AuctionClosingNoticeRepository;
import com.example.ddangddangddang.domain.notification.repository.NotificationRepository;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@SpringBootTest
class NotificationCleanerTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuctionClosingNoticeRepository closingNoticeRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @Test
    @DisplayName("보관 기간(90일)·마감 임박 기록(30일)보다 오래된 것만 지우고, 딱 그 기간인 것은 남긴다")
    void deletesOnlyOlderThanRetention() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        NotificationCleaner cleaner = new NotificationCleaner(notificationRepository, closingNoticeRepository, transactionTemplate,
                true, Duration.ofDays(90), Clock.fixed(now.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));

        Long older = saveNotificationCreatedAt(now.minusDays(90).minusSeconds(1));
        Long exactly = saveNotificationCreatedAt(now.minusDays(90));
        Long recent = saveNotificationCreatedAt(now.minusDays(1));
        closingNoticeRepository.save(new AuctionClosingNotice(990_101L, now.minusDays(30).minusSeconds(1)));
        closingNoticeRepository.save(new AuctionClosingNotice(990_102L, now.minusDays(30)));

        int deleted = cleaner.clean();

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(notificationRepository.findById(older)).isEmpty();
        assertThat(notificationRepository.findById(exactly)).isPresent();
        assertThat(notificationRepository.findById(recent)).isPresent();
        assertThat(closingNoticeRepository.existsById(990_101L)).isFalse();
        assertThat(closingNoticeRepository.existsById(990_102L)).isTrue();
    }

    @Test
    @DisplayName("알림 정리가 실패해도 예외를 밖으로 내지 않고 0건으로 끝내며, 마감 임박 기록 정리는 따로 한다")
    void failureIsSwallowed() {
        NotificationRepository failing = mock(NotificationRepository.class);
        given(failing.findIdsOlderThan(any(), any())).willThrow(new IllegalStateException("DB 연결 끊김"));
        NotificationCleaner cleaner = new NotificationCleaner(failing, closingNoticeRepository, transactionTemplate,
                true, Duration.ofDays(90), Clock.systemDefaultZone());
        closingNoticeRepository.save(new AuctionClosingNotice(990_201L, LocalDateTime.now().minusDays(31)));

        assertThat(cleaner.clean()).isZero();
        assertThat(closingNoticeRepository.existsById(990_201L)).isFalse();
    }

    private Long saveNotificationCreatedAt(LocalDateTime createdAt) {
        Notification saved = notificationRepository.save(Notification.auction(990_001L, NotificationType.AUCTION_ENDED, 1L, "정리 확인", null));
        jdbcTemplate.update("update notifications set created_at = ? where id = ?", createdAt, saved.getId());
        return saved.getId();
    }
}
