package com.example.ddangddangddang.domain.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * 로그인 시도 횟수를 Redis에 센다. 서버가 여러 대여도 같은 키를 보므로 전체 기준으로 제한된다.
 * 첫 시도에서 TTL이 시작되고, TTL이 지나면 키가 사라져 횟수가 초기화된다.
 * <p>
 * login:rate:ip:{IP}       IP별 로그인 요청 횟수 (성공·실패 합산)
 * login:fail:{이메일}       이메일별 연속 실패 횟수. 시도 전에 미리 올리고, 로그인에 성공하면 지운다
 */
@Repository
@RequiredArgsConstructor
public class LoginAttemptRepository {

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
     * IP의 로그인 요청 횟수를 1 늘리고, window 안에서 몇 번째 요청인지 반환한다.
     */
    public long increaseIpCount(String ip, Duration window) {
        return increment("login:rate:ip:" + ip, window);
    }

    /**
     * 이메일의 실패 횟수를 1 늘리고, window 안에서 몇 번째인지 반환한다.
     * 비밀번호를 비교하기 전에 불러 시도를 미리 세므로, 동시에 들어온 요청도 각자 다른 값을 받는다.
     */
    public long increaseFailureCount(String email, Duration window) {
        return increment(failureKey(email), window);
    }

    public void clearFailures(String email) {
        redisTemplate.delete(failureKey(email));
    }

    // 대소문자만 바꿔 같은 계정을 계속 시도하지 못하게 소문자로 맞춘다
    private String failureKey(String email) {
        return "login:fail:" + email.trim().toLowerCase(Locale.ROOT);
    }

    private long increment(String key, Duration window) {
        Long count = redisTemplate.execute(INCREMENT_SCRIPT, List.of(key), String.valueOf(window.toMillis()));
        return count == null ? 0 : count;
    }
}
