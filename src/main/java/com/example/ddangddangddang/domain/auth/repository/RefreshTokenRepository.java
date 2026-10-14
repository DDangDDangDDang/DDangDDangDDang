package com.example.ddangddangddang.domain.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

/**
 * Refresh Token을 Redis에 저장한다. key = refresh:{userId}
 * 사용자당 하나만 저장하므로 다른 기기에서 로그인하면 이전 기기의 Refresh Token은 무효가 된다.
 */
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refresh:";

    /**
     * 저장된 토큰을 확인하고 교체하는 것을 한 번에 실행한다.
     * 확인과 저장을 따로 하면 그 사이에 로그아웃이 토큰을 지워도 새 토큰이 저장되어 로그아웃이 무효가 된다.
     * - 저장된 토큰 == 요청 토큰: 새 토큰으로 교체 → 1
     * - 저장된 토큰 없음(로그아웃·만료): 아무것도 안 함 → 0
     * - 다른 토큰이 저장됨(이미 교체된 토큰 재사용): 탈취 가능성이 있으므로 저장된 토큰도 삭제 → -1
     */
    private static final RedisScript<Long> ROTATE_SCRIPT = RedisScript.of("""
            local current = redis.call('GET', KEYS[1])
            if not current then
                return 0
            end
            if current == ARGV[1] then
                redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
                return 1
            end
            redis.call('DEL', KEYS[1])
            return -1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public void save(Long userId, String refreshToken, Duration ttl) {
        redisTemplate.opsForValue().set(key(userId), refreshToken, ttl);
    }

    public void deleteByUserId(Long userId) {
        redisTemplate.delete(key(userId));
    }

    /**
     * 저장된 토큰이 currentToken일 때만 newToken으로 교체한다.
     */
    public RotateResult rotate(Long userId, String currentToken, String newToken, Duration ttl) {
        Long result = redisTemplate.execute(
                ROTATE_SCRIPT, List.of(key(userId)), currentToken, newToken, String.valueOf(ttl.toMillis()));
        if (result == null || result == 0) {
            return RotateResult.NOT_FOUND;
        }
        return result == 1 ? RotateResult.ROTATED : RotateResult.REUSED;
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    public enum RotateResult {
        ROTATED,    // 교체 성공
        NOT_FOUND,  // 저장된 토큰 없음 (로그아웃·만료)
        REUSED      // 이미 교체된 토큰을 다시 사용 → 저장된 토큰까지 삭제됨
    }
}
