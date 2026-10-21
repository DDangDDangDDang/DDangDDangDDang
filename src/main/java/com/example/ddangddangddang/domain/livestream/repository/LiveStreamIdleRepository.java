package com.example.ddangddangddang.domain.livestream.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * 송출이 멈춘 방송 감시(LiveStreamIdleWatcher)의 상태를 Redis에 둔다. 서버가 여러 대여도 모두 같은 값을 본다.
 *
 * - live:idle:published:{id}   판매자 송출을 마지막으로 확인한 시각(ms)
 * - live:idle:warned:{id}      곧 자동 종료된다고 판매자에게 알렸는지 (있으면 알림)
 * - live:idle:lock             감시 작업 잠금. 한 주기에 한 서버만 검사한다.
 */
@Repository
@RequiredArgsConstructor
public class LiveStreamIdleRepository {

    static final String PUBLISHED_KEY_PREFIX = "live:idle:published:";
    static final String WARNED_KEY_PREFIX = "live:idle:warned:";
    static final String LOCK_KEY = "live:idle:lock";
    // 끝난 방송의 기록이 남지 않도록 넉넉히 잡은 보관 기간
    private static final Duration TTL = Duration.ofDays(1);

    private final StringRedisTemplate redisTemplate;

    public boolean tryLock(Duration ttl) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, "locked", ttl));
    }

    public void markPublished(Long liveStreamId, long epochMillis) {
        redisTemplate.opsForValue().set(PUBLISHED_KEY_PREFIX + liveStreamId, String.valueOf(epochMillis), TTL);
    }

    /** 마지막으로 송출을 확인한 시각(ms). 한 번도 확인하지 못했으면 null */
    public Long getLastPublished(Long liveStreamId) {
        String value = redisTemplate.opsForValue().get(PUBLISHED_KEY_PREFIX + liveStreamId);
        try {
            return value == null ? null : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 알림을 처음 남기면 true, 이미 알렸으면 false */
    public boolean markWarned(Long liveStreamId) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(WARNED_KEY_PREFIX + liveStreamId, "1", TTL));
    }

    /** 알림을 지운다. 지울 알림이 있었으면 true (다시 송출해 자동 종료가 취소됨) */
    public boolean clearWarned(Long liveStreamId) {
        return Boolean.TRUE.equals(redisTemplate.delete(WARNED_KEY_PREFIX + liveStreamId));
    }

    public void clear(Long liveStreamId) {
        redisTemplate.delete(PUBLISHED_KEY_PREFIX + liveStreamId);
        redisTemplate.delete(WARNED_KEY_PREFIX + liveStreamId);
    }
}
