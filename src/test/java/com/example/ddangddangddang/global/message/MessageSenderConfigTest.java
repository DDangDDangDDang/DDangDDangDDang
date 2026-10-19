package com.example.ddangddangddang.global.message;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.AopTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 설정값에 따라 어떤 발송 구현체가 등록되는지 확인한다. (스프링 전체를 띄우지 않고 이 설정 클래스만 띄운다)
 */
class MessageSenderConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MessageSenderConfig.class)
            .withPropertyValues("app.sms.solapi.base-url=https://api.solapi.com");

    @Test
    @DisplayName("기본값(app.sms.enabled 없음)이면 문자를 로그로만 출력한다")
    void defaultIsLog() {
        runner.run(context -> assertThat(context.getBean(SmsSender.class)).isInstanceOf(LogSmsSender.class));
    }

    @Test
    @DisplayName("app.sms.enabled=true면 SOLAPI로 보내고, @Async가 적용돼 요청 스레드와 분리된다")
    void solapi() {
        runner.withPropertyValues("app.sms.enabled=true",
                        "app.sms.solapi.api-key=key", "app.sms.solapi.api-secret=secret", "app.sms.solapi.sender=01000000000")
                .run(context -> {
                    SmsSender smsSender = context.getBean(SmsSender.class);
                    // @Async는 프록시로 동작하므로 빈은 프록시이고, 그 안에 SolapiSmsSender가 들어 있다
                    assertThat(AopUtils.isAopProxy(smsSender)).isTrue();
                    assertThat(AopTestUtils.<SmsSender>getTargetObject(smsSender)).isInstanceOf(SolapiSmsSender.class);
                });
    }

    @Test
    @DisplayName("app.sms.enabled=true인데 키가 비어 있으면 서버가 뜨지 않는다")
    void missingKey() {
        runner.withPropertyValues("app.sms.enabled=true",
                        "app.sms.solapi.api-key=", "app.sms.solapi.api-secret=secret", "app.sms.solapi.sender=01000000000")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("SOLAPI_API_KEY"));
    }
}