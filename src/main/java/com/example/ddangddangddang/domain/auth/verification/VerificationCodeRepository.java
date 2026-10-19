package com.example.ddangddangddang.domain.auth.verification;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * 인증번호와 인증 완료 토큰을 Redis에 저장한다. 모든 키는 TTL이 있어 만료되면 자동으로 사라진다.
 *
 * verify:code:{용도}:{대상}      인증번호
 * verify:attempt:{용도}:{대상}   틀린 시도 횟수
 * verify:cooldown:{용도}:{대상}  재전송 대기 표시
 * verify:token:{용도}:{토큰}     인증 완료 토큰 → 대상 (한 번 쓰면 삭제)
 */
@Repository
@RequiredArgsConstructor
public class VerificationCodeRepository {

    private final StringRedisTemplate redisTemplate;

    /** 재전송 대기 중이 아니면 대기를 시작하고 true. 이미 대기 중이면 false (SET NX로 원자적으로 확인) */
    public boolean tryStartCooldown(VerificationPurpose purpose, String target, Duration cooldown) {
        Boolean started = redisTemplate.opsForValue().setIfAbsent(key("cooldown", purpose, target), "1", cooldown);
        return Boolean.TRUE.equals(started);
    }

    /** 새 인증번호를 저장하고 이전 시도 횟수를 초기화한다. */
    public void saveCode(VerificationPurpose purpose, String target, String code, Duration ttl) {
        redisTemplate.opsForValue().set(key("code", purpose, target), code, ttl);
        redisTemplate.delete(key("attempt", purpose, target));
    }

    public Optional<String> findCode(VerificationPurpose purpose, String target) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key("code", purpose, target)));
    }

    /** 시도 횟수를 1 늘리고 늘어난 값을 반환한다. (INCR이라 동시에 시도해도 횟수가 빠지지 않음) */
    public long increaseAttempts(VerificationPurpose purpose, String target, Duration ttl) {
        String key = key("attempt", purpose, target);
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, ttl);
        }
        return attempts == null ? 0 : attempts;
    }

    public void deleteCode(VerificationPurpose purpose, String target) {
        redisTemplate.delete(key("code", purpose, target));
        redisTemplate.delete(key("attempt", purpose, target));
    }

    public void saveToken(VerificationPurpose purpose, String token, String target, Duration ttl) {
        redisTemplate.opsForValue().set(key("token", purpose, token), target, ttl);
    }

    /** 토큰을 꺼내면서 삭제한다(GETDEL). 같은 토큰을 두 번 쓸 수 없다. */
    public Optional<String> consumeToken(VerificationPurpose purpose, String token) {
        return Optional.ofNullable(redisTemplate.opsForValue().getAndDelete(key("token", purpose, token)));
    }

    private String key(String type, VerificationPurpose purpose, String value) {
        return "verify:" + type + ":" + purpose.name().toLowerCase() + ":" + value;
    }
}