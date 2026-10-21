package com.example.ddangddangddang.domain.livestream.idle;

import com.example.ddangddangddang.domain.livestream.dto.LiveStreamIdleMessage;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.infra.MediaServerClient;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamIdleRepository;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.livestream.service.LiveStreamService;
import com.example.ddangddangddang.global.websocket.broadcast.StompBroadcaster;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 방송 중(LIVE)인데 판매자 송출이 오래 없으면 방송을 자동으로 끝낸다. (카메라·마이크를 모두 끄고 자리를 비웠거나, 창을 닫고 종료를 잊은 경우)
 *
 * - 주기(기본 1분)마다 LiveKit에서 판매자(identity "seller-")가 끄지 않은 트랙(카메라·마이크)을 보내고 있는지 확인해, 마지막으로 송출한 시각을 Redis에 남긴다.
 * - 송출이 없던 시간은 마지막 송출 시각부터 센다. 한 번도 송출하지 않았으면 방송 시작 시각부터 센다.
 * - warn-after(기본 25분)가 지나면 판매자에게 곧 종료된다고 주기마다 알리고(/user/queue/live-stream, 다시 연결해도 받도록),
 *   end-after(기본 30분)가 지나면 판매자가 [방송 종료]를 누른 것과 같이 종료한다. (채팅 종료 안내, 방 삭제) 경매는 방송과 별개라 그대로 둔다.
 * - 알린 뒤 다시 송출하면 자동 종료를 취소하고 판매자에게 알린다.
 * - 서버가 여러 대여도 Redis 잠금으로 한 주기에 한 서버만 검사한다.
 * - LiveKit 조회가 실패하면 그 주기는 아무것도 하지 않는다. (송출 중인 방송을 잘못 끝내지 않도록)
 */
@Slf4j
@Component
@EnableConfigurationProperties(LiveStreamIdleProperties.class)
public class LiveStreamIdleWatcher {

    static final String IDLE_QUEUE = "/queue/live-stream";

    private final LiveStreamRepository liveStreamRepository;
    private final LiveStreamService liveStreamService;
    private final MediaServerClient mediaServerClient;
    private final LiveStreamIdleRepository idleRepository;
    private final StompBroadcaster stompBroadcaster;
    private final LiveStreamIdleProperties properties;
    private final Clock clock;
    private ThreadPoolTaskScheduler scheduler;

    @Autowired
    public LiveStreamIdleWatcher(LiveStreamRepository liveStreamRepository, LiveStreamService liveStreamService,
                                 MediaServerClient mediaServerClient, LiveStreamIdleRepository idleRepository,
                                 StompBroadcaster stompBroadcaster, LiveStreamIdleProperties properties) {
        this(liveStreamRepository, liveStreamService, mediaServerClient, idleRepository, stompBroadcaster, properties,
                Clock.systemDefaultZone());
    }

    LiveStreamIdleWatcher(LiveStreamRepository liveStreamRepository, LiveStreamService liveStreamService,
                          MediaServerClient mediaServerClient, LiveStreamIdleRepository idleRepository,
                          StompBroadcaster stompBroadcaster, LiveStreamIdleProperties properties, Clock clock) {
        this.liveStreamRepository = liveStreamRepository;
        this.liveStreamService = liveStreamService;
        this.mediaServerClient = mediaServerClient;
        this.idleRepository = idleRepository;
        this.stompBroadcaster = stompBroadcaster;
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
        scheduler.setThreadNamePrefix("live-idle-");
        scheduler.initialize();
        scheduler.scheduleWithFixedDelay(this::checkSafely, properties.interval());
        log.info("송출 없는 방송 감시 시작: interval={}, warnAfter={}, endAfter={}",
                properties.interval(), properties.warnAfter(), properties.endAfter());
    }

    @PreDestroy
    void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private void checkSafely() {
        try {
            check();
        } catch (Exception e) {
            log.warn("송출 없는 방송 감시 실패. 다음 주기에 다시 시도합니다.", e);
        }
    }

    /**
     * 한 번 검사한다. 다른 서버가 이번 주기를 이미 검사 중이면 아무것도 하지 않는다.
     */
    void check() {
        // 잠금은 주기보다 조금 짧게 잡아, 다음 주기에는 어느 서버든 다시 잡을 수 있게 한다.
        if (!idleRepository.tryLock(properties.interval().multipliedBy(4).dividedBy(5))) {
            return;
        }
        List<LiveStream> liveStreams = liveStreamRepository.findAllByStatus(LiveStreamStatus.LIVE);
        if (liveStreams.isEmpty()) {
            return;
        }
        Set<String> publishing = mediaServerClient.getPublishingRooms(
                liveStreams.stream().map(LiveStream::getRoomName).toList(), LiveStreamService.PUBLISHER_IDENTITY_PREFIX);

        long now = clock.millis();
        for (LiveStream liveStream : liveStreams) {
            try {
                checkOne(liveStream, publishing.contains(liveStream.getRoomName()), now);
            } catch (Exception e) {
                // 하나가 실패해도(판매자가 같은 순간 직접 종료 등) 나머지는 계속 검사한다.
                log.warn("송출 없는 방송 처리 실패 liveStreamId={}", liveStream.getId(), e);
            }
        }
    }

    private void checkOne(LiveStream liveStream, boolean publishing, long now) {
        Long id = liveStream.getId();
        if (publishing) {
            idleRepository.markPublished(id, now);
            if (idleRepository.clearWarned(id)) {
                stompBroadcaster.sendToUser(liveStream.getSellerId(), IDLE_QUEUE, LiveStreamIdleMessage.resumed(id));
            }
            return;
        }

        Long lastPublished = idleRepository.getLastPublished(id);
        long idleSince = lastPublished != null ? lastPublished : epochMillis(liveStream.getStartedAt(), now);
        long idleMillis = now - idleSince;

        if (idleMillis >= properties.endAfter().toMillis()) {
            log.info("송출이 없어 방송 자동 종료 liveStreamId={}, idleMinutes={}", id, idleMillis / 60_000);
            // 끝내기가 성공한 뒤에만 알린다. (실패하면 방송은 그대로이고 다음 주기에 다시 시도한다)
            // 판매자 화면은 경고를 받던 방송이 끝나도 잠깐 연결을 두어 이 알림을 받는다. (BroadcastProvider)
            liveStreamService.endForIdle(id, liveStream.getSellerId());
            try {
                stompBroadcaster.sendToUser(liveStream.getSellerId(), IDLE_QUEUE, LiveStreamIdleMessage.ended(id));
                idleRepository.clear(id);
            } catch (Exception e) {
                // 방송은 이미 끝났다. 남은 기록은 보관 기간(1일)이 지나면 지워진다.
                log.warn("자동 종료 알림·감시 기록 정리 실패 liveStreamId={}", id, e);
            }
            return;
        }
        if (idleMillis >= properties.warnAfter().toMillis()) {
            idleRepository.markWarned(id);
            // 주기마다 다시 보낸다. 경고 순간 판매자 연결이 끊겨 있었어도 다시 연결하면 다음 주기에 받는다.
            LocalDateTime endsAt = toLocalDateTime(idleSince + properties.endAfter().toMillis());
            stompBroadcaster.sendToUser(liveStream.getSellerId(), IDLE_QUEUE, LiveStreamIdleMessage.warning(id, endsAt));
        }
    }

    // 시작 시각이 없으면(데이터 이상) 지금부터 센다.
    private long epochMillis(LocalDateTime time, long fallback) {
        return time == null ? fallback : time.atZone(clock.getZone()).toInstant().toEpochMilli();
    }

    private LocalDateTime toLocalDateTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), clock.getZone());
    }
}
