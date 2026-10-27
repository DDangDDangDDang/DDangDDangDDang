package com.example.ddangddangddang.domain.livestream.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

/**
 * 손님(로그인하지 않은 사람) 시청 입장 횟수를 IP별로 Redis에 센다. 서버가 여러 대여도 같은 키를 보므로 전체 기준으로 제한된다.
 * 첫 요청에서 TTL이 시작되고, TTL이 지나면 키가 사라져 횟수가 초기화된다.
 * <p>
 * live:guest-join:ip:{IP}  IP별 손님 입장 요청 횟수
 */
@Repository
@RequiredArgsConstructor
public class GuestJoinRateLimitRepository {

    // INCR과 EXPIRE를 한 번에 실행한다. 따로 실행하면 그 사이에 실패했을 때 TTL 없는 키가 남아 영구히 막힌다.
    private static final RedisScript<Long> INCREMENT_SCRIPT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * IP의 손님 입장 횟수를 1 늘리고, window 안에서 몇 번째 요청인지 반환한다.
     */
    public long increase(String ip, Duration window) {
        Long count = redisTemplate.execute(INCREMENT_SCRIPT, List.of("live:guest-join:ip:" + ip), String.valueOf(window.toMillis()));
        return count == null ? 0 : count;
    }
}
