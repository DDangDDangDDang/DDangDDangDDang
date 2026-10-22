package com.example.ddangddangddang.domain.auth.oauth;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/**
 * oauth:signup:{토큰} → 가입 대기 정보(JSON). TTL이 지나면 자동으로 사라진다.
 *
 * 토큰은 추측할 수 없는 32바이트 무작위 값이다. 소셜 회원번호는 Redis에만 있으므로
 * 클라이언트가 남의 카카오 회원번호를 직접 보내 가입하는 일은 생기지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class PendingSocialSignupRepository {

    private static final String KEY_PREFIX = "oauth:signup:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // 저장하고 토큰 반환
    public String save(PendingSocialSignup signup, Duration ttl) {
        String token = newToken();
        redisTemplate.opsForValue().set(KEY_PREFIX + token, objectMapper.writeValueAsString(signup), ttl);
        return token;
    }

    // 지우지 않고 조회만
    public Optional<PendingSocialSignup> find(String token) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + token);
        return Optional.ofNullable(json).map(value -> objectMapper.readValue(value, PendingSocialSignup.class));
    }

    public void delete(String token) {
        redisTemplate.delete(KEY_PREFIX + token);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}