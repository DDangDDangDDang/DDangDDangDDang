package com.example.ddangddangddang.global.security.jwt;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회원 단위 토큰 무효화가 실제 Redis에서 동작하는지 확인한다. Docker가 실행 중이어야 한다.
 */
@Testcontainers
class TokenBlacklistRepositoryTest {

    private static final Instant CHANGED_AT = Instant.parse("2026-10-20T12:00:00.700Z");

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static TokenBlacklistRepository repository;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        repository = new TokenBlacklistRepository(redisTemplate);
    }

    @AfterAll
    static void tearDownRedis() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void clear() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("무효화 이전에 발급된 토큰만 막고, 같은 초나 이후에 발급된 토큰은 통과시킨다")
    void issuedBefore() {
        repository.invalidateTokensIssuedBefore(1L, CHANGED_AT, Duration.ofMinutes(30));

        assertThat(repository.isInvalidatedForUser(1L, Instant.parse("2026-10-20T11:59:59Z"))).isTrue();
        assertThat(repository.isInvalidatedForUser(1L, Instant.parse("2026-10-20T12:00:00Z"))).isFalse();
        assertThat(repository.isInvalidatedForUser(1L, Instant.parse("2026-10-20T12:00:01Z"))).isFalse();
    }

    @Test
    @DisplayName("다른 회원의 토큰과 무효화 기록이 없는 회원은 영향이 없다")
    void otherUser() {
        repository.invalidateTokensIssuedBefore(1L, CHANGED_AT, Duration.ofMinutes(30));

        assertThat(repository.isInvalidatedForUser(2L, Instant.parse("2026-10-20T11:00:00Z"))).isFalse();
    }

    @Test
    @DisplayName("기록은 Access Token 유효시간이 지나면 사라진다")
    void ttl() {
        repository.invalidateTokensIssuedBefore(1L, CHANGED_AT, Duration.ofMinutes(30));

        assertThat(redisTemplate.getExpire("blacklist:user:1")).isBetween(1790L, 1800L);
    }
}