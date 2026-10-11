package com.example.ddangddangddang.global.security.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * 로그아웃된 Access Token을 Redis에 기록한다. key = blacklist:{jti}
 * TTL을 토큰의 남은 유효시간으로 두어, 토큰이 어차피 만료되는 시점에 자동으로 삭제되게 한다.
 */
@Repository
@RequiredArgsConstructor
public class TokenBlacklistRepository {

    private static final String KEY_PREFIX = "blacklist:";
    private static final String LOGGED_OUT = "logout";

    private final StringRedisTemplate redisTemplate;

    public void save(String jti, Duration ttl) {
        redisTemplate.opsForValue().set(KEY_PREFIX + jti, LOGGED_OUT, ttl);
    }

    public boolean exists(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
    }
}