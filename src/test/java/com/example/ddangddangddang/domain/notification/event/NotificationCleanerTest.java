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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NotificationCleanerTest {

    @Autowired
    private NotificationCleaner cleaner;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuctionClosingNoticeRepository closingNoticeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @Test
    @DisplayName("보관 기간(90일)이 지난 알림과 30일이 지난 마감 임박 기록만 지운다")
    void deletesOnlyOldOnes() {
        Notification old = notificationRepository.save(Notification.auction(990_001L, NotificationType.AUCTION_ENDED, 1L, "오래된 알림", null));
        Notification recent = notificationRepository.save(Notification.auction(990_001L, NotificationType.AUCTION_ENDED, 1L, "최근 알림", null));
        jdbcTemplate.update("update notifications set created_at = ? where id = ?", LocalDateTime.now().minusDays(91), old.getId());
        closingNoticeRepository.save(new AuctionClosingNotice(990_101L, LocalDateTime.now().minusDays(31)));
        closingNoticeRepository.save(new AuctionClosingNotice(990_102L, LocalDateTime.now().minusDays(1)));

        int deleted = cleaner.clean();

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(notificationRepository.findById(old.getId())).isEmpty();
        assertThat(notificationRepository.findById(recent.getId())).isPresent();
        assertThat(closingNoticeRepository.existsById(990_101L)).isFalse();
        assertThat(closingNoticeRepository.existsById(990_102L)).isTrue();
    }
}
