package com.example.ddangddangddang.domain.livestream.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 진행 중인 방송의 시청자 수 집계를 Redis에 둔다. 서버가 여러 대여도 모두 같은 값을 본다.
 *
 * - live:viewers                  방송별 현재 시청자 수 (sorted set, 점수 = 시청자 수) → 시청자 많은 방송
 * - live:viewers:surge            방송별 최근 증가 수 (sorted set, 점수 = 증가 수) → 시청자 급증 방송
 * - live:viewers:history:{id}     방송별 최근 시청자 수 기록 ("측정 시각(ms):시청자 수", 최신이 앞) → 증가 수 계산용
 * - live:viewers:lock             집계 작업 잠금. 서버가 여러 대여도 한 주기에 한 서버만 집계한다.
 */
@Repository
@RequiredArgsConstructor
public class LiveViewerStatsRepository {

    static final String VIEWERS_KEY = "live:viewers";
    static final String SURGE_KEY = "live:viewers:surge";
    static final String HISTORY_KEY_PREFIX = "live:viewers:history:";
    static final String LOCK_KEY = "live:viewers:lock";

    private final StringRedisTemplate redisTemplate;

    public boolean tryLock(Duration ttl) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, "locked", ttl));
    }

    /**
     * 시청자 수 기록을 하나 추가한다. 최신 기록이 앞에 오고, maxSamples개까지만 남긴다.
     * 방송이 끝나 더 기록하지 않으면 ttl 뒤에 사라진다.
     */
    public void appendSample(Long liveStreamId, Sample sample, int maxSamples, Duration ttl) {
        String key = HISTORY_KEY_PREFIX + liveStreamId;
        redisTemplate.opsForList().leftPush(key, sample.epochMillis() + ":" + sample.viewers());
        redisTemplate.opsForList().trim(key, 0, maxSamples - 1);
        redisTemplate.expire(key, ttl);
    }

    /** 최근 시청자 수 기록 (최신이 앞) */
    public List<Sample> getSamples(Long liveStreamId) {
        List<String> values = redisTemplate.opsForList().range(HISTORY_KEY_PREFIX + liveStreamId, 0, -1);
        if (values == null) {
            return List.of();
        }
        List<Sample> samples = new ArrayList<>(values.size());
        for (String value : values) {
            int colon = value.indexOf(':');
            samples.add(new Sample(Long.parseLong(value.substring(0, colon)), Integer.parseInt(value.substring(colon + 1))));
        }
        return samples;
    }

    /**
     * 순위를 이번 집계 결과로 통째로 바꾼다. 끝난 방송은 결과에 없으므로 순위에서 자연히 빠진다.
     * 임시 키에 쓴 뒤 RENAME으로 바꿔, 읽는 쪽이 반쯤 바뀐 순위를 보지 않게 한다.
     */
    public void replaceRankings(Map<Long, Integer> viewers, Map<Long, Integer> increases) {
        replace(VIEWERS_KEY, viewers);
        replace(SURGE_KEY, increases);
    }

    private void replace(String key, Map<Long, Integer> scores) {
        if (scores.isEmpty()) {
            redisTemplate.delete(key);
            return;
        }
        // 집계가 잠금 시간보다 오래 걸려 두 서버가 동시에 바꾸더라도 서로의 임시 키를 섞지 않도록 호출마다 고유한 키를 쓴다.
        String tempKey = key + ":tmp:" + UUID.randomUUID();
        scores.forEach((liveStreamId, score) -> redisTemplate.opsForZSet().add(tempKey, String.valueOf(liveStreamId), score));
        redisTemplate.rename(tempKey, key);
    }

    /** 시청자 많은 순 */
    public List<Ranked> topViewers(int count) {
        return toRanked(redisTemplate.opsForZSet().reverseRangeWithScores(VIEWERS_KEY, 0, count - 1));
    }

    /** 최근 증가 수가 큰 순. 늘어난 방송(증가 수 1 이상)만 */
    public List<Ranked> topSurging(int count) {
        return toRanked(redisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(SURGE_KEY, 1, Double.POSITIVE_INFINITY, 0, count));
    }

    /** 방송별 현재 시청자 수. 집계된 적 없는 방송은 결과에 없다. */
    public Map<Long, Integer> getViewerCounts(Collection<Long> liveStreamIds) {
        return scores(VIEWERS_KEY, liveStreamIds);
    }

    /** 방송별 최근 증가 수. 집계된 적 없는 방송은 결과에 없다. */
    public Map<Long, Integer> getIncreases(Collection<Long> liveStreamIds) {
        return scores(SURGE_KEY, liveStreamIds);
    }

    private Map<Long, Integer> scores(String key, Collection<Long> liveStreamIds) {
        if (liveStreamIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = List.copyOf(liveStreamIds);
        List<Double> values = redisTemplate.opsForZSet().score(key, ids.stream().map(String::valueOf).toArray());
        Map<Long, Integer> result = new HashMap<>();
        for (int i = 0; i < ids.size() && values != null && i < values.size(); i++) {
            if (values.get(i) != null) {
                result.put(ids.get(i), values.get(i).intValue());
            }
        }
        return result;
    }

    private List<Ranked> toRanked(Set<TypedTuple<String>> tuples) {
        if (tuples == null) {
            return List.of();
        }
        return tuples.stream()
                .filter(tuple -> tuple.getValue() != null && tuple.getScore() != null)
                .map(tuple -> new Ranked(Long.parseLong(tuple.getValue()), tuple.getScore().intValue()))
                .toList();
    }

    public record Sample(long epochMillis, int viewers) {
    }

    public record Ranked(Long liveStreamId, int score) {
    }
}
