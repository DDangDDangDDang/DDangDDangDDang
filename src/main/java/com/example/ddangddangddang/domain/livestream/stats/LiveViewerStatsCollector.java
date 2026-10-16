package com.example.ddangddangddang.domain.livestream.stats;

import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.infra.MediaServerClient;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.livestream.repository.LiveViewerStatsRepository;
import com.example.ddangddangddang.domain.livestream.repository.LiveViewerStatsRepository.Sample;
import com.example.ddangddangddang.domain.livestream.service.LiveStreamService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 진행 중인 방송의 시청자 수를 LiveKit에서 주기적으로 가져와 Redis 순위(시청자 수, 최근 증가 수)를 갱신한다.
 *
 * - 시청자 수: LiveKit 방의 참가자 중 시청 입장 토큰(identity "viewer-{회원 ID}")으로 접속한 사람 수. 판매자는 세지 않는다.
 *   같은 회원이 여러 탭으로 들어와도 한 명이다. (같은 identity는 LiveKit이 이전 접속을 끊음)
 * - 증가 수: 지금 시청자 수 - surge-window(기본 5분) 전 시청자 수. 시작한 지 그보다 짧은 방송은 처음 측정한 값과 비교한다.
 * - 서버가 여러 대여도 Redis 잠금으로 한 주기에 한 서버만 집계한다.
 * - LiveKit이나 Redis가 실패해도 다음 주기에 다시 시도하며, 방송·채팅 기능에는 영향이 없다. (홈 순위만 잠시 이전 값)
 */
@Slf4j
@Component
@EnableConfigurationProperties(LiveViewerStatsProperties.class)
public class LiveViewerStatsCollector {

    // 기록은 surge-window를 덮을 만큼만 남긴다. (주기 15초, 5분이면 21개 + 여유)
    private static final int EXTRA_SAMPLES = 4;

    private final LiveStreamRepository liveStreamRepository;
    private final MediaServerClient mediaServerClient;
    private final LiveViewerStatsRepository statsRepository;
    private final LiveViewerStatsProperties properties;
    private final Clock clock;
    private ThreadPoolTaskScheduler scheduler;

    @Autowired
    public LiveViewerStatsCollector(LiveStreamRepository liveStreamRepository, MediaServerClient mediaServerClient,
                                    LiveViewerStatsRepository statsRepository, LiveViewerStatsProperties properties) {
        this(liveStreamRepository, mediaServerClient, statsRepository, properties, Clock.systemDefaultZone());
    }

    LiveViewerStatsCollector(LiveStreamRepository liveStreamRepository, MediaServerClient mediaServerClient,
                             LiveViewerStatsRepository statsRepository, LiveViewerStatsProperties properties, Clock clock) {
        this.liveStreamRepository = liveStreamRepository;
        this.mediaServerClient = mediaServerClient;
        this.statsRepository = statsRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @PostConstruct
    void start() {
        if (!properties.enabled()) {
            return;
        }
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("live-viewer-stats-");
        scheduler.initialize();
        scheduler.scheduleWithFixedDelay(this::collectSafely, properties.interval());
        log.info("방송 시청자 수 집계 시작: interval={}, surgeWindow={}", properties.interval(), properties.surgeWindow());
    }

    @PreDestroy
    void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private void collectSafely() {
        try {
            collect();
        } catch (Exception e) {
            log.warn("방송 시청자 수 집계 실패. 다음 주기에 다시 시도합니다.", e);
        }
    }

    /**
     * 한 번 집계한다. 다른 서버가 이번 주기를 이미 집계 중이면 아무것도 하지 않는다.
     */
    void collect() {
        // 잠금은 주기보다 조금 짧게 잡아, 다음 주기에는 어느 서버든 다시 잡을 수 있게 한다.
        if (!statsRepository.tryLock(properties.interval().multipliedBy(4).dividedBy(5))) {
            return;
        }
        List<LiveStream> liveStreams = liveStreamRepository.findAllByStatus(LiveStreamStatus.LIVE);
        Map<String, Integer> counts = liveStreams.isEmpty()
                ? Map.of()
                : mediaServerClient.getViewerCounts(liveStreams.stream().map(LiveStream::getRoomName).toList(),
                        LiveStreamService.VIEWER_IDENTITY_PREFIX);

        long now = clock.millis();
        int maxSamples = (int) (properties.surgeWindow().toMillis() / properties.interval().toMillis()) + EXTRA_SAMPLES;
        Duration historyTtl = properties.surgeWindow().plus(properties.interval().multipliedBy(EXTRA_SAMPLES));

        Map<Long, Integer> viewers = new HashMap<>();
        Map<Long, Integer> increases = new HashMap<>();
        for (LiveStream liveStream : liveStreams) {
            int current = counts.getOrDefault(liveStream.getRoomName(), 0);
            List<Sample> history = statsRepository.getSamples(liveStream.getId());
            viewers.put(liveStream.getId(), current);
            increases.put(liveStream.getId(), increase(current, history, now, properties.surgeWindow()));
            statsRepository.appendSample(liveStream.getId(), new Sample(now, current), maxSamples, historyTtl);
        }
        statsRepository.replaceRankings(viewers, increases);
    }

    /**
     * 지금 시청자 수가 window 전보다 몇 명 늘었는지. 줄었으면 음수.
     * 비교 기준은 window 전 시점이나 그보다 이전의 가장 최근 기록이고, 그런 기록이 없으면(시작한 지 얼마 안 된 방송) 가장 오래된 기록이다.
     * 기록이 하나도 없으면(첫 측정) 0이다.
     *
     * @param history 최근 기록 (최신이 앞)
     */
    static int increase(int current, List<Sample> history, long now, Duration window) {
        if (history.isEmpty()) {
            return 0;
        }
        long boundary = now - window.toMillis();
        Sample baseline = history.get(history.size() - 1);
        for (Sample sample : history) {
            if (sample.epochMillis() <= boundary) {
                baseline = sample;
                break;
            }
        }
        return current - baseline.viewers();
    }
}
