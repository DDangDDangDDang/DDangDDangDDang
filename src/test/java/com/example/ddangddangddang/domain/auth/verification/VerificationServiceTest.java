package com.example.ddangddangddang.domain.auth.verification;

import com.example.ddangddangddang.global.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 재전송 제한(SET NX), 시도 횟수(INCR), 일회용 토큰(GETDEL)은 Redis 명령의 동작에 기대므로 실제 Redis에서 확인한다.
 * Docker가 실행 중이어야 한다.
 */
@Testcontainers
class VerificationServiceTest {

    private static final String PHONE = "01012345678";

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static VerificationService verificationService;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        verificationService = new VerificationService(
                new VerificationCodeRepository(redisTemplate), new VerificationRateLimitRepository(redisTemplate));    }

    @AfterAll
    static void tearDownRedis() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void clear() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Nested
    @DisplayName("인증번호 발급")
    class IssueCode {

        @Test
        @DisplayName("6자리 숫자 인증번호를 발급한다")
        void sixDigits() {
            String code = verificationService.issueCode(VerificationPurpose.FIND_EMAIL, PHONE);

            assertThat(code).matches("\\d{6}");
        }

        @Test
        @DisplayName("1분 안에 다시 요청하면 예외가 발생한다")
        void resendTooSoon() {
            verificationService.issueCode(VerificationPurpose.FIND_EMAIL, PHONE);

            assertThatThrownBy(() -> verificationService.issueCode(VerificationPurpose.FIND_EMAIL, PHONE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(VerificationErrorCode.RESEND_TOO_SOON);
        }

        @Test
        @DisplayName("같은 대상에는 하루 10번까지만 발급하고, 용도를 바꿔도 같이 센다")
        void dailyLimitPerTarget() {
            for (int i = 0; i < VerificationService.DAILY_LIMIT_PER_TARGET; i++) {
                VerificationPurpose purpose = i % 2 == 0 ? VerificationPurpose.FIND_EMAIL : VerificationPurpose.SIGNUP_PHONE;
                verificationService.issueCode(purpose, PHONE);
                clearCooldown(purpose);
            }

            assertThatThrownBy(() -> verificationService.issueCode(VerificationPurpose.FIND_EMAIL, PHONE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(VerificationErrorCode.DAILY_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("하루 제한은 대상마다 따로 센다")
        void dailyLimitIsPerTarget() {
            for (int i = 0; i < VerificationService.DAILY_LIMIT_PER_TARGET; i++) {
                verificationService.issueCode(VerificationPurpose.FIND_EMAIL, PHONE);
                clearCooldown(VerificationPurpose.FIND_EMAIL);
            }

            String code = verificationService.issueCode(VerificationPurpose.FIND_EMAIL, "01099999999");

            assertThat(code).hasSize(6);
        }

        // 1분 재전송 제한을 기다리지 않고 다음 발급을 하기 위해 대기 키만 지운다
        private void clearCooldown(VerificationPurpose purpose) {
            redisTemplate.delete("verify:cooldown:" + purpose.name().toLowerCase() + ":" + PHONE);
        }

        @Test
        @DisplayName("용도가 다르면 재전송 제한을 따로 적용한다")
        void cooldownPerPurpose() {
            verificationService.issueCode(VerificationPurpose.FIND_EMAIL, PHONE);

            String code = verificationService.issueCode(VerificationPurpose.SIGNUP_PHONE, PHONE);

            assertThat(code).hasSize(6);
        }
    }

    @Nested
    @DisplayName("인증번호 검증")
    class VerifyCode {

        @Test
        @DisplayName("맞으면 인증 완료 토큰을 주고, 토큰으로 인증 대상을 꺼낼 수 있다")
        void success() {
            String code = verificationService.issueCode(VerificationPurpose.FIND_EMAIL, PHONE);

            String token = verificationService.verifyCode(VerificationPurpose.FIND_EMAIL, PHONE, code);

            assertThat(verificationService.consumeToken(VerificationPurpose.FIND_EMAIL, token)).isEqualTo(PHONE);
        }

        @Test
        @DisplayName("한 번 쓴 인증번호는 다시 쓸 수 없다")
        void codeIsOneTime() {
            String code = verificationService.issueCode(VerificationPurpose.FIND_EMAIL, PHONE);
            verificationService.verifyCode(VerificationPurpose.FIND_EMAIL, PHONE, code);

            assertThatThrownBy(() -> verificationService.verifyCode(VerificationPurpose.FIND_EMAIL, PHONE, code))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(VerificationErrorCode.INVALID_CODE);
        }

        @Test
        @DisplayName("틀리면 INVALID_CODE")
        void wrongCode() {
            String code = verificationService.issueCode(VerificationPurpose.FIND_EMAIL, PHONE);

            assertThatThrownBy(() -> verificationService.verifyCode(VerificationPurpose.FIND_EMAIL, PHONE, wrong(code)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(VerificationErrorCode.INVALID_CODE);
        }

        @Test
        @DisplayName("5번 틀리면 6번째는 맞는 번호여도 거부하고 인증번호를 지운다")
        void tooManyAttempts() {
            String code = verificationService.issueCode(VerificationPurpose.FIND_EMAIL, PHONE);
            for (int i = 0; i < VerificationService.MAX_ATTEMPTS; i++) {
                assertThatThrownBy(() -> verificationService.verifyCode(VerificationPurpose.FIND_EMAIL, PHONE, wrong(code)))
                        .extracting("errorCode")
                        .isEqualTo(VerificationErrorCode.INVALID_CODE);
            }

            assertThatThrownBy(() -> verificationService.verifyCode(VerificationPurpose.FIND_EMAIL, PHONE, code))
                    .extracting("errorCode")
                    .isEqualTo(VerificationErrorCode.TOO_MANY_ATTEMPTS);
            assertThatThrownBy(() -> verificationService.verifyCode(VerificationPurpose.FIND_EMAIL, PHONE, code))
                    .extracting("errorCode")
                    .isEqualTo(VerificationErrorCode.INVALID_CODE);
        }

        /**
         * 확인(GET)과 삭제(DEL)를 따로 하면, 맞는 인증번호로 동시에 요청했을 때
         * 둘 다 "아직 남아 있음"을 보고 통과해 토큰이 여러 개 발급된다.
         */
        @Test
        @DisplayName("맞는 인증번호로 동시에 여러 번 요청해도 토큰은 하나만 발급된다")
        void concurrentVerify() throws Exception {
            String code = verificationService.issueCode(VerificationPurpose.FIND_EMAIL, PHONE);
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> results = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        verificationService.verifyCode(VerificationPurpose.FIND_EMAIL, PHONE, code);
                        return true;
                    } catch (BusinessException e) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int issued = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    issued++;
                }
            }
            executor.shutdown();

            assertThat(issued).isEqualTo(1);
        }

        @Test
        @DisplayName("발급받지 않은 대상이면 INVALID_CODE")
        void notIssued() {
            assertThatThrownBy(() -> verificationService.verifyCode(VerificationPurpose.FIND_EMAIL, PHONE, "123456"))
                    .extracting("errorCode")
                    .isEqualTo(VerificationErrorCode.INVALID_CODE);
        }
    }

    @Nested
    @DisplayName("인증 완료 토큰")
    class ConsumeToken {

        @Test
        @DisplayName("한 번 쓰면 다시 쓸 수 없다")
        void oneTime() {
            String token = verifiedToken(VerificationPurpose.RESET_PASSWORD, "user@test.com");
            verificationService.consumeToken(VerificationPurpose.RESET_PASSWORD, token);

            assertThatThrownBy(() -> verificationService.consumeToken(VerificationPurpose.RESET_PASSWORD, token))
                    .extracting("errorCode")
                    .isEqualTo(VerificationErrorCode.INVALID_VERIFICATION_TOKEN);
        }

        @Test
        @DisplayName("다른 용도로 받은 토큰은 쓸 수 없다")
        void otherPurpose() {
            String token = verifiedToken(VerificationPurpose.SIGNUP_PHONE, PHONE);

            assertThatThrownBy(() -> verificationService.consumeToken(VerificationPurpose.RESET_PASSWORD, token))
                    .extracting("errorCode")
                    .isEqualTo(VerificationErrorCode.INVALID_VERIFICATION_TOKEN);
        }

        private String verifiedToken(VerificationPurpose purpose, String target) {
            String code = verificationService.issueCode(purpose, target);
            return verificationService.verifyCode(purpose, target, code);
        }
    }

    private static String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }
}