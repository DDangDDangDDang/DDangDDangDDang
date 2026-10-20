package com.example.ddangddangddang.domain.auth.verification;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 횟수 증가와 만료 시간 설정이 실제 Redis에서 한 번에 동작하는지 확인한다. Docker가 실행 중이어야 한다.
 */
@Testcontainers
class VerificationRateLimitRepositoryTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static VerificationRateLimitRepository repository;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        repository = new VerificationRateLimitRepository(redisTemplate);
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
    @DisplayName("요청마다 1씩 늘고, 첫 요청에서 만료 시간이 걸린다")
    void incrementWithTtl() {
        assertThat(repository.increaseIpCount("203.0.113.7", Duration.ofHours(1))).isEqualTo(1);
        assertThat(repository.increaseIpCount("203.0.113.7", Duration.ofHours(1))).isEqualTo(2);

        Long ttlSeconds = redisTemplate.getExpire("verify:rate:ip:203.0.113.7");
        assertThat(ttlSeconds).isBetween(3500L, 3600L);
    }

    @Test
    @DisplayName("IP와 대상, 서로 다른 IP는 따로 센다")
    void separateKeys() {
        repository.increaseIpCount("203.0.113.7", Duration.ofHours(1));
        repository.increaseIpCount("203.0.113.7", Duration.ofHours(1));

        assertThat(repository.increaseIpCount("198.51.100.1", Duration.ofHours(1))).isEqualTo(1);
        assertThat(repository.increaseTargetCount("01012345678", Duration.ofDays(1))).isEqualTo(1);
    }
}