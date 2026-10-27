package com.example.ddangddangddang.global.security.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;

import java.time.Duration;

/**
 * 더 이상 쓰면 안 되는 Access Token을 Redis에 기록한다.
 * - blacklist:{jti}          로그아웃한 토큰 하나
 * - blacklist:user:{userId}  이 시각(초) 이전에 발급된 그 회원의 토큰 전부 (비밀번호 변경 등)
 * TTL을 토큰의 (남은) 유효시간으로 두어, 토큰이 어차피 만료되는 시점에 자동으로 삭제되게 한다.
 */
@Repository
@RequiredArgsConstructor
public class TokenBlacklistRepository {

    private static final String KEY_PREFIX = "blacklist:";
    private static final String LOGGED_OUT = "logout";
    private static final String USER_KEY_PREFIX = "blacklist:user:";

    private final StringRedisTemplate redisTemplate;

    public void save(String jti, Duration ttl) {
        redisTemplate.opsForValue().set(KEY_PREFIX + jti, LOGGED_OUT, ttl);
    }

    public boolean exists(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
    }

    /**
     * 회원의 토큰 중 invalidatedAt 이전에 발급된 것을 모두 무효로 한다.
     * 토큰을 하나하나 알 필요 없이 "이 시각 이전 발급분"으로 한 번에 막는다.
     * ttl은 Access Token 유효시간으로 준다. 그 시간이 지나면 이전 토큰은 어차피 모두 만료되어 기록이 필요 없다.
     */
    public void invalidateTokensIssuedBefore(Long userId, Instant invalidatedAt, Duration ttl) {
        redisTemplate.opsForValue().set(USER_KEY_PREFIX + userId, String.valueOf(invalidatedAt.getEpochSecond()), ttl);
    }

    /**
     * JWT의 발급 시각(iat)은 초 단위라 초 단위로 비교한다.
     * 무효화와 같은 초에 발급된 토큰은 통과시킨다. (비밀번호를 바꾸고 바로 다시 로그인한 토큰이 막히지 않게)
     */
    public boolean isInvalidatedForUser(Long userId, Instant issuedAt) {
        String invalidatedAt = redisTemplate.opsForValue().get(USER_KEY_PREFIX + userId);
        return invalidatedAt != null && issuedAt.getEpochSecond() < Long.parseLong(invalidatedAt);
    }
}