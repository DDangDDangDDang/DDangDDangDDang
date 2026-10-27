package com.example.ddangddangddang.domain.directmessage.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

/**
 * 회원별 1:1 채팅 전송 횟수를 Redis에 둔다. 서버가 여러 대여도 같은 키를 보므로 회원 단위로 제한된다.
 * 라이브 채팅(chat:rate:)과 키를 나눠, 방송 채팅을 많이 쳤다고 1:1 채팅이 막히지 않게 한다.
 * - dm:rate:{userId}: 전송 횟수. 첫 메시지에서 TTL이 시작되고, TTL이 지나면 키가 사라져 횟수가 초기화된다.
 */
@Repository
@RequiredArgsConstructor
public class DmRateLimitRepository {

    private static final String RATE_KEY_PREFIX = "dm:rate:";

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
     * 전송 횟수를 1 늘리고, window 안에서 몇 번째 메시지인지 반환한다.
     */
    public long increment(Long userId, Duration window) {
        Long count = redisTemplate.execute(
                INCREMENT_SCRIPT, List.of(RATE_KEY_PREFIX + userId), String.valueOf(window.toMillis()));
        return count == null ? 0 : count;
    }
}
