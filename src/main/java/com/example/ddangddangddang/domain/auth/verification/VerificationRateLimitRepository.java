package com.example.ddangddangddang.domain.auth.verification;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

/**
 * 인증번호 요청 횟수를 Redis에 센다. 서버가 여러 대여도 같은 키를 보므로 전체 기준으로 제한된다.
 * 첫 요청에서 TTL이 시작되고, TTL이 지나면 키가 사라져 횟수가 초기화된다.
 * <p>
 * verify:rate:ip:{IP}        IP별 인증번호 요청 횟수 (용도 구분 없이 합산)
 * verify:rate:target:{대상}  휴대폰 번호·이메일별 인증번호 발급 횟수 (용도 구분 없이 합산)
 */
@Repository
@RequiredArgsConstructor
public class VerificationRateLimitRepository {

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
     * IP의 요청 횟수를 1 늘리고, window 안에서 몇 번째 요청인지 반환한다.
     */
    public long increaseIpCount(String ip, Duration window) {
        return increment("verify:rate:ip:" + ip, window);
    }

    /**
     * 대상(휴대폰 번호·이메일)의 발급 횟수를 1 늘리고, window 안에서 몇 번째 발급인지 반환한다.
     */
    public long increaseTargetCount(String target, Duration window) {
        return increment("verify:rate:target:" + target, window);
    }

    private long increment(String key, Duration window) {
        Long count = redisTemplate.execute(INCREMENT_SCRIPT, List.of(key), String.valueOf(window.toMillis()));
        return count == null ? 0 : count;
    }
}