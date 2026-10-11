package com.example.ddangddangddang.domain.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * Refresh Token을 Redis에 저장한다. key = refresh:{userId}
 * 사용자당 하나만 저장하므로 다른 기기에서 로그인하면 이전 기기의 Refresh Token은 무효가 된다.
 */
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;

    public void save(Long userId, String refreshToken, Duration ttl) {
        redisTemplate.opsForValue().set(key(userId), refreshToken, ttl);
    }

    public Optional<String> findByUserId(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(userId)));
    }

    public void deleteByUserId(Long userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}