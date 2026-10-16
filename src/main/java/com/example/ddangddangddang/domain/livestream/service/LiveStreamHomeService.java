package com.example.ddangddangddang.domain.livestream.service;

import com.example.ddangddangddang.domain.livestream.dto.LiveStreamHomeResponse;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.livestream.repository.LiveViewerStatsRepository;
import com.example.ddangddangddang.domain.livestream.repository.LiveViewerStatsRepository.Ranked;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 홈 화면 방송 목록 (시청자 많은 방송, 시청자 급증 방송, 방송 예정). 시청자 수 순위는 LiveViewerStatsCollector가 주기적으로 Redis에 갱신한 값을 읽는다.
 *
 * 순위는 최대 한 주기(기본 15초) 늦을 수 있어, 그 사이 끝난 방송이 순위에 남아 있을 수 있다.
 * 그래서 DB에서 방송 상태를 다시 확인해 진행 중인 방송만 보여 준다.
 * Redis를 읽지 못하면 시청자 수 없이 최근 시작한 방송만 보여 준다. (홈 화면이 통째로 실패하지 않게)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LiveStreamHomeService {

    static final int MAX_SIZE = 20;
    // 순위에 남아 있을 수 있는 끝난 방송을 걸러낸 뒤에도 size개를 채울 수 있도록 조금 더 읽는다.
    private static final int EXTRA = 10;
    // 방송 예정 목록에 시작 예정 시간이 조금 지났지만 아직 시작하지 않은 방송도 남겨 둔다. (판매자가 조금 늦게 시작하는 경우)
    // 그보다 오래 지난 방송은 시작하지 않을 가능성이 커서 목록에서 뺀다.
    static final Duration UPCOMING_GRACE = Duration.ofMinutes(30);

    private final LiveStreamRepository liveStreamRepository;
    private final LiveViewerStatsRepository statsRepository;
    private final LiveStreamCardAssembler cardAssembler;

    public LiveStreamHomeResponse getHome(int size) {
        int pageSize = Math.max(1, Math.min(size, MAX_SIZE));

        List<Ranked> viewerRanking = List.of();
        List<Ranked> surgeRanking = List.of();
        try {
            viewerRanking = statsRepository.topViewers(pageSize + EXTRA);
            surgeRanking = statsRepository.topSurging(pageSize + EXTRA);
        } catch (RuntimeException e) {
            log.warn("시청자 수 순위를 읽지 못해 최근 시작한 방송만 보여 줍니다.", e);
        }

        List<LiveStream> mostViewed = liveOnly(viewerRanking, pageSize);
        fillWithRecent(mostViewed, pageSize);
        List<LiveStream> surging = liveOnly(surgeRanking, pageSize);
        List<LiveStream> upcoming = liveStreamRepository.findByStatusAndScheduledStartAtGreaterThanEqualOrderByScheduledStartAtAsc(
                LiveStreamStatus.SCHEDULED, LocalDateTime.now().minus(UPCOMING_GRACE), PageRequest.of(0, pageSize));

        return new LiveStreamHomeResponse(
                cardAssembler.toCards(mostViewed), cardAssembler.toCards(surging), cardAssembler.toCards(upcoming));
    }

    // 순위 순서를 지키면서 DB에서 진행 중인 방송만 남긴다.
    private List<LiveStream> liveOnly(List<Ranked> ranking, int limit) {
        if (ranking.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, LiveStream> streams = liveStreamRepository.findAllById(ranking.stream().map(Ranked::liveStreamId).toList())
                .stream().collect(Collectors.toMap(LiveStream::getId, Function.identity()));
        return ranking.stream()
                .map(ranked -> streams.get(ranked.liveStreamId()))
                .filter(stream -> stream != null && stream.getStatus() == LiveStreamStatus.LIVE)
                .limit(limit)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    // 시청자 수가 아직 집계되지 않은 방송(막 시작함, 집계 꺼짐)도 홈에 나오도록 최근 시작한 방송으로 채운다.
    private void fillWithRecent(List<LiveStream> streams, int limit) {
        if (streams.size() >= limit) {
            return;
        }
        Set<Long> included = streams.stream().map(LiveStream::getId).collect(Collectors.toCollection(HashSet::new));
        for (LiveStream recent : liveStreamRepository.findByStatusOrderByStartedAtDesc(
                LiveStreamStatus.LIVE, PageRequest.of(0, limit + included.size()))) {
            if (streams.size() >= limit) {
                break;
            }
            if (included.add(recent.getId())) {
                streams.add(recent);
            }
        }
    }
}
