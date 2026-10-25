package com.example.ddangddangddang.domain.auth.oauth;

import com.example.ddangddangddang.domain.user.entity.SocialProvider;
import java.time.Duration;
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
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 가입 대기 토큰을 실제 Redis에서 확인한다. Docker가 실행 중이어야 한다.
 */
@Testcontainers
class PendingSocialSignupRepositoryTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static PendingSocialSignupRepository repository;

    private final PendingSocialSignup signup =
            new PendingSocialSignup(SocialProvider.KAKAO, "1234567890", "user@kakao.com", "카카오닉");

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        repository = new PendingSocialSignupRepository(redisTemplate, JsonMapper.builder().build());
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
    @DisplayName("저장하면 추측할 수 없는 토큰을 주고, 그 토큰으로 TTL과 함께 조회된다")
    void saveAndFind() {
        String token = repository.save(signup, Duration.ofMinutes(10));

        assertThat(token).hasSize(43); // 32바이트 Base64 URL (패딩 없음)
        assertThat(repository.find(token)).contains(signup);
        assertThat(redisTemplate.getExpire("oauth:signup:" + token)).isBetween(1L, 600L);
    }

    @Test
    @DisplayName("조회만 해서는 지워지지 않고, delete 후에는 없다")
    void findDoesNotDelete() {
        String token = repository.save(signup, Duration.ofMinutes(10));

        repository.find(token);
        assertThat(repository.find(token)).isPresent();

        repository.delete(token);
        assertThat(repository.find(token)).isEmpty();
    }

    @Test
    @DisplayName("이메일·닉네임이 없어도(null) 저장된다")
    void nullFields() {
        PendingSocialSignup noConsent = new PendingSocialSignup(SocialProvider.KAKAO, "1", null, null);

        String token = repository.save(noConsent, Duration.ofMinutes(10));

        assertThat(repository.find(token)).contains(noConsent);
    }

    @Test
    @DisplayName("없는 토큰은 비어 있다")
    void unknownToken() {
        assertThat(repository.find("unknown")).isEmpty();
    }
}
