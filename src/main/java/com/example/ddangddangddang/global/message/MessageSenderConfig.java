package com.example.ddangddangddang.global.message;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@EnableAsync
@Configuration
public class MessageSenderConfig {

    @Bean
    @ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
    public EmailSender smtpEmailSender(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
        return new SmtpEmailSender(mailSender, from);
    }

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    public EmailSender logEmailSender() {
        return new LogEmailSender();
    }

    /**
     * app.sms.enabled=true일 때만 실제 문자를 보낸다.
     * 키가 비어 있으면 첫 문자를 보낼 때가 아니라 서버를 띄울 때 바로 실패시켜 설정 실수를 빨리 알아차리게 한다.
     */
    @Bean
    @ConditionalOnProperty(name = "app.sms.enabled", havingValue = "true")
    public SmsSender solapiSmsSender(@Value("${app.sms.solapi.base-url}") String baseUrl,
                                     @Value("${app.sms.solapi.api-key}") String apiKey,
                                     @Value("${app.sms.solapi.api-secret}") String apiSecret,
                                     @Value("${app.sms.solapi.sender}") String sender) {
        Assert.hasText(apiKey, "SOLAPI_API_KEY가 필요합니다.");
        Assert.hasText(apiSecret, "SOLAPI_API_SECRET이 필요합니다.");
        Assert.hasText(sender, "SOLAPI_SENDER(사전 등록한 발신번호)가 필요합니다.");

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        return new SolapiSmsSender(restClient, apiKey, apiSecret, sender);
    }

    @Bean
    @ConditionalOnMissingBean(SmsSender.class)
    public SmsSender logSmsSender() {
        return new LogSmsSender();
    }
}