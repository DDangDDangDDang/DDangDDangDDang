package com.example.ddangddangddang.global.message;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 실제 SOLAPI를 부르지 않고, 가짜 서버(MockRestServiceServer)로 요청 형식과 응답 처리를 확인한다.
 * @Async는 스프링 빈으로 부를 때만 동작하므로, 여기서는 send()가 같은 스레드에서 바로 실행된다.
 */
@ExtendWith(OutputCaptureExtension.class)
class SolapiSmsSenderTest {

    private static final String BASE_URL = "https://api.solapi.com";

    private MockRestServiceServer server;
    private SolapiSmsSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new SolapiSmsSender(builder.build(), "test-key", "test-secret", "01000000000");
    }

    @Test
    @DisplayName("발송 API에 수신번호, 발신번호, 내용을 담아 서명한 인증 헤더와 함께 보낸다")
    void request() {
        server.expect(requestTo(BASE_URL + SolapiSmsSender.SEND_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, org.hamcrest.Matchers.matchesPattern(
                        "HMAC-SHA256 Apikey=test-key, Date=\\S+, salt=[0-9a-f]{32}, signature=[0-9a-f]{64}")))
                .andExpect(content().json("""
                        {"messages": [{"to": "01012345678", "from": "01000000000", "text": "인증번호 [123456]"}]}
                        """))
                .andRespond(withSuccess("{\"failedMessageList\": []}", MediaType.APPLICATION_JSON));

        sender.send("01012345678", "인증번호 [123456]");

        server.verify();
    }

    @Test
    @DisplayName("서명은 (시각 + salt)를 시크릿 키로 HMAC-SHA256한 16진수 값이다")
    void signature() {
        // 기대값은 Java가 아닌 다른 도구(Python hmac)로 따로 계산했다
        String header = sender.authorization("2026-10-16T00:00:00Z", "0123456789abcdef0123456789abcdef");

        assertThat(header).isEqualTo("HMAC-SHA256 Apikey=test-key, Date=2026-10-16T00:00:00Z, "
                + "salt=0123456789abcdef0123456789abcdef, "
                + "signature=d1e3accd5601e3e4658085cc99454ec8839c421f8cd89baccb5cd255f3d59052");
    }

    @Test
    @DisplayName("접수는 됐지만 실패한 메시지가 있으면 상태 코드를 로그로 남기고, 인증번호는 로그에 남기지 않는다")
    void failedMessage(CapturedOutput output) {
        server.expect(requestTo(BASE_URL + SolapiSmsSender.SEND_PATH))
                .andRespond(withSuccess("""
                        {"failedMessageList": [{"to": "01012345678", "statusCode": "1062", "statusMessage": "발신번호 미등록"}]}
                        """, MediaType.APPLICATION_JSON));

        sender.send("01012345678", "인증번호 [123456]");

        assertThat(output).contains("문자 발송 실패", "010****5678", "1062", "발신번호 미등록");
        assertThat(output).doesNotContain("123456", "01012345678");
    }

    @Test
    @DisplayName("SOLAPI 서버 오류여도 예외를 던지지 않고 로그만 남긴다")
    void serverError(CapturedOutput output) {
        server.expect(requestTo(BASE_URL + SolapiSmsSender.SEND_PATH)).andRespond(withServerError());

        assertThatCode(() -> sender.send("01012345678", "인증번호 [123456]")).doesNotThrowAnyException();
        assertThat(output).contains("문자 발송 요청 실패", "010****5678");
    }

    @Test
    @DisplayName("로그에 남기는 번호는 가운데 4자리를 가린다")
    void mask() {
        assertThat(SolapiSmsSender.mask("01012345678")).isEqualTo("010****5678");
        assertThat(SolapiSmsSender.mask("0101234567")).isEqualTo("010****4567");
        assertThat(SolapiSmsSender.mask(null)).isEqualTo("****");
    }
}