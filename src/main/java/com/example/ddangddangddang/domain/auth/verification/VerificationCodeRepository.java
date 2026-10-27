package com.example.ddangddangddang.domain.auth.verification;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
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

    /**
     * KEYS[1] 인증번호, KEYS[2] 시도 횟수 / ARGV[1] 입력한 번호, ARGV[2] 시도 횟수 TTL(초), ARGV[3] 최대 시도 횟수
     * - 인증번호가 없거나(만료·미발급) 틀림 → 0
     * - 맞음 → 인증번호와 시도 횟수 삭제 → 1
     * - 시도 횟수 초과 → 인증번호와 시도 횟수 삭제 → -1 (맞는 번호여도 거부)
     */
    private static final RedisScript<Long> VERIFY_SCRIPT = RedisScript.of("""
            local saved = redis.call('GET', KEYS[1])
            if not saved then
                return 0
            end
            local attempts = redis.call('INCR', KEYS[2])
            if attempts == 1 then
                redis.call('EXPIRE', KEYS[2], ARGV[2])
            end
            if attempts > tonumber(ARGV[3]) then
                redis.call('DEL', KEYS[1], KEYS[2])
                return -1
            end
            if saved ~= ARGV[1] then
                return 0
            end
            redis.call('DEL', KEYS[1], KEYS[2])
            return 1
            """, Long.class);

    /**
     * KEYS[1] 인증 완료 토큰 / ARGV[1] 기대하는 대상(휴대폰 번호 등)
     * - 토큰이 없거나 다른 대상의 토큰 → 0 (지우지 않음)
     * - 같은 대상 → 삭제 → 1
     */
    private static final RedisScript<Long> CONSUME_IF_MATCHES_SCRIPT = RedisScript.of("""
            local saved = redis.call('GET', KEYS[1])
            if not saved or saved ~= ARGV[1] then
                return 0
            end
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);

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

    /**
     * 인증번호 확인, 시도 횟수 증가, 삭제를 Lua 스크립트로 한 번에 실행한다.
     * 따로 실행하면 맞는 인증번호로 동시에 요청했을 때 둘 다 "아직 남아 있음"을 보고 통과해 토큰이 여러 개 발급된다.
     * Redis는 스크립트 하나를 다른 명령과 섞지 않고 끝까지 실행하므로 그 사이에 끼어들 수 없다.
     */
    public CodeCheckResult verifyAndDeleteCode(VerificationPurpose purpose, String target, String code,
                                               Duration ttl, int maxAttempts) {
        Long result = redisTemplate.execute(VERIFY_SCRIPT,
                List.of(key("code", purpose, target), key("attempt", purpose, target)),
                code, String.valueOf(ttl.toSeconds()), String.valueOf(maxAttempts));
        if (result == null || result == 0) {
            return CodeCheckResult.INVALID;
        }
        return result == 1 ? CodeCheckResult.VERIFIED : CodeCheckResult.TOO_MANY_ATTEMPTS;
    }

    public void saveToken(VerificationPurpose purpose, String token, String target, Duration ttl) {
        redisTemplate.opsForValue().set(key("token", purpose, token), target, ttl);
    }
    /**
     * 토큰이 기대한 대상의 것일 때만 삭제하고 true. 확인과 삭제를 Lua 스크립트로 한 번에 실행한다.
     * GETDEL로 먼저 꺼낸 뒤 비교하면, 대상이 달라 실패한 요청도 토큰을 지워 버린다.
     */
    public boolean consumeTokenIfMatches(VerificationPurpose purpose, String token, String expectedTarget) {
        Long result = redisTemplate.execute(CONSUME_IF_MATCHES_SCRIPT,
                List.of(key("token", purpose, token)), expectedTarget);
        return result != null && result == 1;
    }
    
    /** 토큰을 꺼내면서 삭제한다(GETDEL). 같은 토큰을 두 번 쓸 수 없다. */
    public Optional<String> consumeToken(VerificationPurpose purpose, String token) {
        return Optional.ofNullable(redisTemplate.opsForValue().getAndDelete(key("token", purpose, token)));
    }

    private String key(String type, VerificationPurpose purpose, String value) {
        return "verify:" + type + ":" + purpose.name().toLowerCase() + ":" + value;
    }

    public enum CodeCheckResult {
        VERIFIED,           // 맞음 (인증번호 삭제됨)
        INVALID,            // 없거나 틀림
        TOO_MANY_ATTEMPTS   // 시도 횟수 초과 (인증번호 삭제됨)
    }
}