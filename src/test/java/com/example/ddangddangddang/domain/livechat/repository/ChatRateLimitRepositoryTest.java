package com.example.ddangddangddang.domain.livechat.repository;

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
 * 같은 문장 반복 세기(직전 내용과 같으면 +1, 다르면 1부터)를 실제 Redis에서 확인한다.
 * Docker가 실행 중이어야 한다.
 */
@Testcontainers
class ChatRateLimitRepositoryTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static ChatRateLimitRepository repository;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        repository = new ChatRateLimitRepository(redisTemplate);
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
    @DisplayName("같은 내용을 연달아 보내면 1씩 늘고, 다른 내용을 보내면 1부터 다시 센다")
    void countsConsecutiveSameContent() {
        Duration window = Duration.ofSeconds(30);

        assertThat(repository.countRepeated(1L, "입찰", window)).isEqualTo(1);
        assertThat(repository.countRepeated(1L, "입찰", window)).isEqualTo(2);
        assertThat(repository.countRepeated(1L, "입찰", window)).isEqualTo(3);
        assertThat(repository.countRepeated(1L, "사이즈요", window)).isEqualTo(1);
        assertThat(repository.countRepeated(1L, "입찰", window)).isEqualTo(1);
    }

    @Test
    @DisplayName("마지막으로 보낸 뒤 window가 지나면 다시 1부터 세고, 회원별로 따로 센다")
    void resetsAfterWindowAndPerUser() throws Exception {
        repository.countRepeated(1L, "입찰", Duration.ofMillis(200));
        repository.countRepeated(1L, "입찰", Duration.ofMillis(200));
        assertThat(repository.countRepeated(2L, "입찰", Duration.ofMillis(200))).isEqualTo(1);

        Thread.sleep(300);

        assertThat(repository.countRepeated(1L, "입찰", Duration.ofMillis(200))).isEqualTo(1);
    }
}
