package com.example.ddangddangddang.domain.livestream.stats;

import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.infra.MediaServerClient;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.livestream.repository.LiveViewerStatsRepository;
import com.example.ddangddangddang.domain.livestream.repository.LiveViewerStatsRepository.Sample;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

class LiveViewerStatsCollectorTest {

    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final long NOW = 1_000_000_000L;

    @Nested
    @DisplayName("증가 수 계산")
    class Increase {

        @Test
        @DisplayName("5분 전이나 그 이전의 가장 최근 기록과 비교한다")
        void compareWithWindowStart() {
            List<Sample> history = List.of(            // 최신이 앞
                    sample(NOW - 60_000, 40),          // 1분 전
                    sample(NOW - 300_000, 25),         // 5분 전 ← 기준
                    sample(NOW - 360_000, 10));        // 6분 전

            assertThat(LiveViewerStatsCollector.increase(50, history, NOW, WINDOW)).isEqualTo(25);
        }

        @Test
        @DisplayName("시작한 지 5분이 안 된 방송은 가장 오래된 기록과 비교하고, 줄었으면 음수다")
        void youngStreamAndDecrease() {
            List<Sample> history = List.of(sample(NOW - 30_000, 12), sample(NOW - 90_000, 8));

            assertThat(LiveViewerStatsCollector.increase(20, history, NOW, WINDOW)).isEqualTo(12);
            assertThat(LiveViewerStatsCollector.increase(3, history, NOW, WINDOW)).isEqualTo(-5);
        }

        @Test
        @DisplayName("처음 측정하면(기록 없음) 0이다")
        void firstMeasurement() {
            assertThat(LiveViewerStatsCollector.increase(30, List.of(), NOW, WINDOW)).isZero();
        }
    }

    @Nested
    @DisplayName("집계")
    @ExtendWith(MockitoExtension.class)
    class Collect {

        @Mock
        private LiveStreamRepository liveStreamRepository;
        @Mock
        private MediaServerClient mediaServerClient;
        @Mock
        private LiveViewerStatsRepository statsRepository;

        private LiveViewerStatsCollector collector() {
            LiveViewerStatsProperties properties = new LiveViewerStatsProperties(true, Duration.ofSeconds(15), WINDOW);
            return new LiveViewerStatsCollector(liveStreamRepository, mediaServerClient, statsRepository, properties,
                    Clock.fixed(Instant.ofEpochMilli(NOW), ZoneId.of("Asia/Seoul")));
        }

        @Test
        @DisplayName("진행 중인 방송의 시청자 수를 기록하고, 시청자 수·증가 수 순위를 통째로 바꾼다. LiveKit에 없는 방은 0명이다")
        void collect() {
            LiveStream first = liveStream(1L);
            LiveStream second = liveStream(2L);
            given(statsRepository.tryLock(any())).willReturn(true);
            given(liveStreamRepository.findAllByStatus(LiveStreamStatus.LIVE)).willReturn(List.of(first, second));
            given(mediaServerClient.getViewerCounts(List.of(first.getRoomName(), second.getRoomName()), "viewer-"))
                    .willReturn(Map.of(first.getRoomName(), 30));
            given(statsRepository.getSamples(1L)).willReturn(List.of(sample(NOW - 300_000, 10)));
            given(statsRepository.getSamples(2L)).willReturn(List.of());

            collector().collect();

            then(statsRepository).should().appendSample(eq(1L), eq(new Sample(NOW, 30)), anyInt(), any());
            then(statsRepository).should().appendSample(eq(2L), eq(new Sample(NOW, 0)), anyInt(), any());
            then(statsRepository).should().replaceRankings(Map.of(1L, 30, 2L, 0), Map.of(1L, 20, 2L, 0));
        }

        @Test
        @DisplayName("다른 서버가 이번 주기를 집계 중이면(잠금 실패) 아무것도 하지 않는다")
        void skipWhenLocked() {
            given(statsRepository.tryLock(any())).willReturn(false);

            collector().collect();

            then(liveStreamRepository).shouldHaveNoInteractions();
            then(mediaServerClient).shouldHaveNoInteractions();
            then(statsRepository).should(never()).replaceRankings(any(), any());
        }

        @Test
        @DisplayName("진행 중인 방송이 없으면 LiveKit에 묻지 않고 순위를 비운다")
        void noLiveStreams() {
            given(statsRepository.tryLock(any())).willReturn(true);
            given(liveStreamRepository.findAllByStatus(LiveStreamStatus.LIVE)).willReturn(List.of());

            collector().collect();

            then(mediaServerClient).shouldHaveNoInteractions();
            then(statsRepository).should(never()).appendSample(anyLong(), any(), anyInt(), any());
            then(statsRepository).should().replaceRankings(Map.of(), Map.of());
        }

        @Test
        @DisplayName("잠금은 주기보다 짧게 잡아 다음 주기에 다시 집계할 수 있게 한다")
        void lockShorterThanInterval() {
            given(statsRepository.tryLock(any())).willReturn(false);

            collector().collect();

            ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
            then(statsRepository).should().tryLock(ttl.capture());
            assertThat(ttl.getValue()).isLessThan(Duration.ofSeconds(15)).isPositive();
        }
    }

    private static Sample sample(long epochMillis, int viewers) {
        return new Sample(epochMillis, viewers);
    }

    private static LiveStream liveStream(Long id) {
        LiveStream liveStream = LiveStream.create(100L + id, "방송 " + id);
        ReflectionTestUtils.setField(liveStream, "id", id);
        liveStream.start(100L + id);
        return liveStream;
    }
}
