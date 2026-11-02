package com.example.ddangddangddang.domain.livechat.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 회원별 채팅 도배 방지 정보를 Redis에 둔다. 서버가 여러 대여도 같은 키를 보므로 회원 단위로 제한된다.
 * - chat:rate:{userId}: 전송 횟수. 첫 메시지에서 TTL이 시작되고, TTL이 지나면 키가 사라져 횟수가 초기화된다.
 * - chat:mute:{userId}: 채팅 금지 표시. TTL이 지나면 자동으로 풀린다.
 * - chat:repeat:{userId}: 직전에 보낸 내용과 연달아 보낸 횟수. (같은 문장 반복 도배 방지)
 */
@Repository
@RequiredArgsConstructor
public class ChatRateLimitRepository {

    private static final String RATE_KEY_PREFIX = "chat:rate:";
    private static final String MUTE_KEY_PREFIX = "chat:mute:";
    private static final String REPEAT_KEY_PREFIX = "chat:repeat:";
    private static final String MUTED = "muted";

    // INCR과 EXPIRE를 한 번에 실행한다. 따로 실행하면 그 사이에 실패했을 때 TTL 없는 키가 남아 영구히 채팅이 막힌다.
    private static final RedisScript<Long> INCREMENT_SCRIPT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    // 직전 메시지와 내용이 같으면 횟수를 늘리고, 다르면 새 내용으로 1부터 센다. 보낼 때마다 TTL을 다시 건다.
    private static final RedisScript<Long> REPEAT_SCRIPT = RedisScript.of("""
            local count
            if redis.call('HGET', KEYS[1], 'content') == ARGV[1] then
                count = redis.call('HINCRBY', KEYS[1], 'count', 1)
            else
                redis.call('HSET', KEYS[1], 'content', ARGV[1], 'count', 1)
                count = 1
            end
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 같은 내용을 연달아 몇 번째 보내는지 반환한다. 직전 메시지와 window 넘게 떨어지거나 내용이 다르면 1부터 다시 센다.
     * chat:repeat:{userId}에 직전 내용과 횟수를 둔다.
     */
    public long countRepeated(Long userId, String content, Duration window) {
        Long count = redisTemplate.execute(
                REPEAT_SCRIPT, List.of(REPEAT_KEY_PREFIX + userId), content, String.valueOf(window.toMillis()));
        return count == null ? 0 : count;
    }

    /**
     * 전송 횟수를 1 늘리고, window 안에서 몇 번째 메시지인지 반환한다.
     */
    public long increment(Long userId, Duration window) {
        Long count = redisTemplate.execute(
                INCREMENT_SCRIPT, List.of(RATE_KEY_PREFIX + userId), String.valueOf(window.toMillis()));
        return count == null ? 0 : count;
    }

    public void mute(Long userId, Duration duration) {
        redisTemplate.opsForValue().set(MUTE_KEY_PREFIX + userId, MUTED, duration);
    }

    /**
     * 채팅 금지가 풀리기까지 남은 시간. 금지 중이 아니면 Duration.ZERO.
     * 키가 없으면 Redis가 음수(-2)를 돌려주므로 0으로 바꾼다.
     */
    public Duration getMuteRemaining(Long userId) {
        Long remainingMillis = redisTemplate.getExpire(MUTE_KEY_PREFIX + userId, TimeUnit.MILLISECONDS);
        if (remainingMillis == null || remainingMillis <= 0) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(remainingMillis);
    }
}
