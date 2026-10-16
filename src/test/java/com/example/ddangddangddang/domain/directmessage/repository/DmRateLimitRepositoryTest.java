package com.example.ddangddangddang.domain.directmessage.repository;

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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1:1 채팅 도배 횟수 세기(INCR + 첫 메시지에서 TTL)를 실제 Redis에서 확인한다.
 * Docker가 실행 중이어야 한다.
 */
@Testcontainers
class DmRateLimitRepositoryTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static DmRateLimitRepository repository;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        repository = new DmRateLimitRepository(redisTemplate);
    }

    @AfterAll
    static void tearDownRedis() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void clear() {
        redisTemplate.delete(redisTemplate.keys("*"));
    }

    @Test
    @DisplayName("보낼 때마다 1씩 늘고, 첫 메시지에서 TTL이 잡히며 이후 메시지가 TTL을 늘리지 않는다")
    void countWithinWindow() throws Exception {
        assertThat(repository.increment(1L, Duration.ofSeconds(10))).isEqualTo(1);
        Long firstTtl = redisTemplate.getExpire("dm:rate:1", TimeUnit.MILLISECONDS);
        Thread.sleep(50);

        assertThat(repository.increment(1L, Duration.ofSeconds(10))).isEqualTo(2);
        assertThat(redisTemplate.getExpire("dm:rate:1", TimeUnit.MILLISECONDS)).isLessThan(firstTtl);
    }

    @Test
    @DisplayName("TTL이 지나면 횟수가 초기화되고, 회원별로 따로 센다")
    void resetsAfterWindowAndPerUser() throws Exception {
        repository.increment(1L, Duration.ofMillis(200));
        repository.increment(1L, Duration.ofMillis(200));
        assertThat(repository.increment(2L, Duration.ofMillis(200))).isEqualTo(1);

        Thread.sleep(300);

        assertThat(repository.increment(1L, Duration.ofMillis(200))).isEqualTo(1);
    }

    @Test
    @DisplayName("라이브 채팅 도배 키(chat:rate:)와 섞이지 않는다")
    void separateFromLiveChat() {
        redisTemplate.opsForValue().set("chat:rate:1", "99");

        assertThat(repository.increment(1L, Duration.ofSeconds(10))).isEqualTo(1);
    }
}
