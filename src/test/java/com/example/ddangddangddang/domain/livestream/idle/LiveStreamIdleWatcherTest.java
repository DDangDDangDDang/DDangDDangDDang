package com.example.ddangddangddang.domain.livestream.idle;

import com.example.ddangddangddang.domain.livestream.dto.LiveStreamIdleMessage;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.infra.MediaServerClient;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamIdleRepository;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.livestream.service.LiveStreamService;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.domain.livestream.exception.LiveStreamErrorCode;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 송출이 멈춘 방송: 25분이면 판매자에게 알리고, 30분이면 종료한다. 다시 송출하면 자동 종료를 취소한다.
 */
@ExtendWith(MockitoExtension.class)
class LiveStreamIdleWatcherTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-10-21T06:00:00Z");
    private static final long NOW_MS = NOW.toEpochMilli();
    private static final String QUEUE = "/queue/live-stream";

    @Mock
    private LiveStreamRepository liveStreamRepository;
    @Mock
    private LiveStreamService liveStreamService;
    @Mock
    private MediaServerClient mediaServerClient;
    @Mock
    private LiveStreamIdleRepository idleRepository;
    @Mock
    private StompBroadcaster stompBroadcaster;

    private LiveStreamIdleWatcher watcher;

    @BeforeEach
    void setUp() {
        LiveStreamIdleProperties properties = new LiveStreamIdleProperties(true, Duration.ofMinutes(1), Duration.ofMinutes(25), Duration.ofMinutes(30));
        watcher = new LiveStreamIdleWatcher(liveStreamRepository, liveStreamService, mediaServerClient, idleRepository,
                stompBroadcaster, properties, Clock.fixed(NOW, ZONE));
        given(idleRepository.tryLock(any())).willReturn(true);
    }

    @Test
    @DisplayName("송출 중이면 마지막 송출 시각을 남기고, 알렸던 방송이면 자동 종료 취소를 알린다")
    void publishing() {
        LiveStream stream = live(1L, 10L, minutesAgo(40));
        given(liveStreamRepository.findAllByStatus(LiveStreamStatus.LIVE)).willReturn(List.of(stream));
        given(mediaServerClient.getPublishingRooms(List.of("room-1"), "seller-")).willReturn(Set.of("room-1"));
        given(idleRepository.clearWarned(1L)).willReturn(true);

        watcher.check();

        verify(idleRepository).markPublished(1L, NOW_MS);
        verify(stompBroadcaster).sendToUser(10L, QUEUE, LiveStreamIdleMessage.resumed(1L));
        verify(liveStreamService, never()).endForIdle(anyLong(), anyLong());
    }

    @Test
    @DisplayName("한 번도 송출하지 않았으면 방송 시작부터 세어, 25분이 지나면 판매자에게 알린다")
    void warnAfter25Minutes() {
        LiveStream stream = live(1L, 10L, minutesAgo(26));
        given(liveStreamRepository.findAllByStatus(LiveStreamStatus.LIVE)).willReturn(List.of(stream));
        given(mediaServerClient.getPublishingRooms(any(), any())).willReturn(Set.of());
        given(idleRepository.getLastPublished(1L)).willReturn(null);

        watcher.check();

        verify(idleRepository).markWarned(1L);

        LocalDateTime endsAt = minutesAgo(26).plusMinutes(30);
        verify(stompBroadcaster).sendToUser(10L, QUEUE, LiveStreamIdleMessage.warning(1L, endsAt));
        verify(liveStreamService, never()).endForIdle(anyLong(), anyLong());
    }

    @Test
    @DisplayName("이미 알렸어도 종료될 때까지 주기마다 다시 알린다 (경고 순간 연결이 끊겨 있던 판매자도 다시 연결하면 받도록)")
    void warnEveryCycle() {
        LiveStream stream = live(1L, 10L, minutesAgo(60));
        given(liveStreamRepository.findAllByStatus(LiveStreamStatus.LIVE)).willReturn(List.of(stream));
        given(mediaServerClient.getPublishingRooms(any(), any())).willReturn(Set.of());
        long lastPublished = NOW_MS - Duration.ofMinutes(27).toMillis();
        given(idleRepository.getLastPublished(1L)).willReturn(lastPublished);

        watcher.check();
        watcher.check();

        LocalDateTime endsAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastPublished), ZONE).plusMinutes(30);
        verify(stompBroadcaster, org.mockito.Mockito.times(2)).sendToUser(10L, QUEUE, LiveStreamIdleMessage.warning(1L, endsAt));
    }

    @Test
    @DisplayName("마지막 송출부터 30분이 지나면 방송을 종료하고 판매자에게 알린다")
    void endAfter30Minutes() {
        LiveStream stream = live(1L, 10L, minutesAgo(90));
        given(liveStreamRepository.findAllByStatus(LiveStreamStatus.LIVE)).willReturn(List.of(stream));
        given(mediaServerClient.getPublishingRooms(any(), any())).willReturn(Set.of());
        given(idleRepository.getLastPublished(1L)).willReturn(NOW_MS - Duration.ofMinutes(30).toMillis());

        watcher.check();

        // 끝내기가 성공한 뒤에 알린다.
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(stompBroadcaster, liveStreamService);
        order.verify(liveStreamService).endForIdle(1L, 10L);
        order.verify(stompBroadcaster).sendToUser(10L, QUEUE, LiveStreamIdleMessage.ended(1L));
        verify(idleRepository).clear(1L);
    }

    @Test
    @DisplayName("종료한 뒤 감시 기록 정리가 실패해도 오류로 끝내지 않는다")
    void clearFailureAfterEnd() {
        LiveStream stream = live(1L, 10L, minutesAgo(90));
        given(liveStreamRepository.findAllByStatus(LiveStreamStatus.LIVE)).willReturn(List.of(stream));
        given(mediaServerClient.getPublishingRooms(any(), any())).willReturn(Set.of());
        willThrow(new IllegalStateException("redis down")).given(idleRepository).clear(1L);

        org.assertj.core.api.Assertions.assertThatCode(() -> watcher.check()).doesNotThrowAnyException();
        verify(liveStreamService).endForIdle(1L, 10L);
    }

    @Test
    @DisplayName("송출이 멈춘 지 25분이 안 됐으면 아무것도 하지 않는다 (방송은 오래됐어도 최근에 송출했으면)")
    void recentlyPublished() {
        LiveStream stream = live(1L, 10L, minutesAgo(120));
        given(liveStreamRepository.findAllByStatus(LiveStreamStatus.LIVE)).willReturn(List.of(stream));
        given(mediaServerClient.getPublishingRooms(any(), any())).willReturn(Set.of());
        given(idleRepository.getLastPublished(1L)).willReturn(NOW_MS - Duration.ofMinutes(10).toMillis());

        watcher.check();

        verify(idleRepository, never()).markWarned(anyLong());
        verify(liveStreamService, never()).endForIdle(anyLong(), anyLong());
    }

    @Test
    @DisplayName("LiveKit 조회가 실패하면 어떤 방송도 끝내지 않는다")
    void mediaServerFailure() {
        LiveStream stream = live(1L, 10L, minutesAgo(90));
        given(liveStreamRepository.findAllByStatus(LiveStreamStatus.LIVE)).willReturn(List.of(stream));
        willThrow(new BusinessException(LiveStreamErrorCode.MEDIA_SERVER_ERROR)).given(mediaServerClient).getPublishingRooms(any(), any());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> watcher.check()).isInstanceOf(BusinessException.class);
        verify(liveStreamService, never()).endForIdle(anyLong(), anyLong());
    }

    @Test
    @DisplayName("한 방송의 종료가 실패해도(판매자가 같은 순간 직접 종료 등) 다른 방송은 계속 검사한다")
    void continueOnFailure() {
        LiveStream first = live(1L, 10L, minutesAgo(90));
        LiveStream second = live(2L, 20L, minutesAgo(90));
        given(liveStreamRepository.findAllByStatus(LiveStreamStatus.LIVE)).willReturn(List.of(first, second));
        given(mediaServerClient.getPublishingRooms(any(), any())).willReturn(Set.of());
        willThrow(new BusinessException(LiveStreamErrorCode.NOT_LIVE)).given(liveStreamService).endForIdle(1L, 10L);

        watcher.check();

        verify(liveStreamService).endForIdle(eq(2L), eq(20L));
        // 끝내지 못한 방송은 종료 알림을 보내지 않는다.
        verify(stompBroadcaster, never()).sendToUser(10L, QUEUE, LiveStreamIdleMessage.ended(1L));
    }

    @Test
    @DisplayName("다른 서버가 이번 주기를 검사 중이면 아무것도 하지 않는다")
    void locked() {
        given(idleRepository.tryLock(any())).willReturn(false);

        watcher.check();

        verify(liveStreamRepository, never()).findAllByStatus(any());
    }

    private LocalDateTime minutesAgo(long minutes) {
        return LocalDateTime.ofInstant(NOW, ZONE).minusMinutes(minutes);
    }

    private LiveStream live(Long id, Long sellerId, LocalDateTime startedAt) {
        LiveStream stream = mock(LiveStream.class);
        org.mockito.Mockito.lenient().when(stream.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(stream.getSellerId()).thenReturn(sellerId);
        org.mockito.Mockito.lenient().when(stream.getRoomName()).thenReturn("room-" + id);
        org.mockito.Mockito.lenient().when(stream.getStartedAt()).thenReturn(startedAt);
        return stream;
    }
}
