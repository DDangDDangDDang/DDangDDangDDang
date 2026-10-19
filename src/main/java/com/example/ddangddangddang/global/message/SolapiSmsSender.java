package com.example.ddangddangddang.global.message;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SOLAPI로 문자를 보낸다. (https://developers.solapi.com)
 *
 * 공식 Java SDK는 Kotlin, OkHttp, Retrofit을 함께 가져와서, 문자 한 통 보내는 데 쓰기엔 무겁다.
 * 그래서 SDK와 같은 API(/messages/v4/send-many/fast)를 RestClient로 직접 호출한다.
 */
@Slf4j
public class SolapiSmsSender implements SmsSender {

    static final String SEND_PATH = "/messages/v4/send-many/fast";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final RestClient restClient;
    private final String apiKey;
    private final String apiSecret;
    private final String senderNumber;

    public SolapiSmsSender(RestClient restClient, String apiKey, String apiSecret, String senderNumber) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.senderNumber = senderNumber;
    }

    /**
     * 외부 API 호출이라 느리거나 실패할 수 있으므로 요청 스레드와 분리해 비동기로 보낸다.
     * 실패해도 예외를 던지지 않고 로그만 남긴다. (사용자는 인증번호 재요청으로 다시 받을 수 있다)
     * 문자 내용에는 인증번호가 있으므로 로그에 남기지 않는다.
     */
    @Async
    @Override
    public void send(String phoneNumber, String text) {
        Map<String, Object> body = Map.of("messages", List.of(
                Map.of("to", phoneNumber, "from", senderNumber, "text", text)));
        try {
            JsonNode response = restClient.post()
                    .uri(SEND_PATH)
                    .header(HttpHeaders.AUTHORIZATION, authorization(Instant.now().toString(), newSalt()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            logFailedMessages(phoneNumber, response);
        } catch (RestClientException e) {
            log.error("문자 발송 요청 실패: to={}", mask(phoneNumber), e);
        }
    }

    /**
     * SOLAPI 인증 헤더. 시크릿 키 자체는 보내지 않고, 매 요청마다 (시각 + 무작위 값)을 시크릿으로 서명해 보낸다.
     * 서버는 같은 방식으로 서명을 다시 계산해 비교하고, 시각이 오래됐거나 같은 salt가 다시 오면 거부한다. (재전송 공격 방지)
     */
    String authorization(String date, String salt) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            String signature = HexFormat.of().formatHex(mac.doFinal((date + salt).getBytes(StandardCharsets.UTF_8)));
            return "HMAC-SHA256 Apikey=" + apiKey + ", Date=" + date + ", salt=" + salt + ", signature=" + signature;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SOLAPI 서명 생성 실패", e);
        }
    }

    /** 요청이 접수돼도 번호 오류, 발신번호 미등록 등은 응답의 failedMessageList로 알려준다. */
    private void logFailedMessages(String phoneNumber, JsonNode response) {
        if (response == null) {
            return;
        }
        for (JsonNode failed : response.path("failedMessageList")) {
            log.error("문자 발송 실패: to={}, statusCode={}, statusMessage={}", mask(phoneNumber),
                    failed.path("statusCode").asString(), failed.path("statusMessage").asString());
        }
    }

    private String newSalt() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 01012345678 → 010****5678 */
    static String mask(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 8) {
            return "****";
        }
        return phoneNumber.substring(0, 3) + "****" + phoneNumber.substring(phoneNumber.length() - 4);
    }
}