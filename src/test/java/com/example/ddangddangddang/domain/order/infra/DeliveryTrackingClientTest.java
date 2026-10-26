package com.example.ddangddangddang.domain.order.infra;

import com.example.ddangddangddang.domain.order.entity.Courier;
import com.example.ddangddangddang.domain.order.exception.OrderErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 실제 배송 조회 서비스를 부르지 않고, 가짜 서버(MockRestServiceServer)로 GraphQL 요청 형식과 응답 처리를 확인한다.
 */
class DeliveryTrackingClientTest {

    private static final String API_URL = "https://apis.tracker.delivery/graphql";
    private static final DeliveryTrackerProperties PROPERTIES =
            new DeliveryTrackerProperties("client-id", "client-secret", null, null, null);

    private MockRestServiceServer server;
    private DeliveryTrackingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DeliveryTrackingClient(builder.build(), PROPERTIES);
    }

    @Test
    @DisplayName("택배사 ID와 송장번호를 GraphQL 변수로 보내고, 인증 헤더를 붙이고, 이벤트를 오래된 순으로 받는다")
    void track() {
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "TRACKQL-API-KEY client-id:client-secret"))
                .andExpect(jsonPath("$.variables.carrierId").value("kr.cjlogistics"))
                .andExpect(jsonPath("$.variables.trackingNumber").value("123456789012"))
                .andRespond(withSuccess("""
                        {"data": {"track": {
                          "lastEvent": {"time": "2026-10-26T18:10:00+09:00", "status": {"code": "OUT_FOR_DELIVERY", "name": "배송출발"},
                                        "description": "배송기사가 출발했습니다", "location": {"name": "강남대리점"}},
                          "events": {"edges": [
                            {"node": {"time": "2026-10-25T20:00:00+09:00", "status": {"code": "AT_PICKUP", "name": "집화처리"},
                                      "description": null, "location": {"name": "곤지암Hub"}}},
                            {"node": {"time": "2026-10-26T18:10:00+09:00", "status": {"code": "OUT_FOR_DELIVERY", "name": "배송출발"},
                                      "description": "배송기사가 출발했습니다", "location": {"name": "강남대리점"}}}
                          ]}
                        }}}
                        """, MediaType.APPLICATION_JSON));

        TrackingInfo info = client.track(Courier.CJ, "123456789012");

        assertThat(info.lastEvent()).isEqualTo(new TrackingEvent(
                "2026-10-26T18:10:00+09:00", "OUT_FOR_DELIVERY", "배송출발", "강남대리점", "배송기사가 출발했습니다"));
        assertThat(info.events()).extracting(TrackingEvent::statusCode).containsExactly("AT_PICKUP", "OUT_FOR_DELIVERY");
        assertThat(info.events().get(0).description()).isNull();
        assertThat(info.isDelivered()).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("택배사마다 맞는 carrierId로 조회한다")
    void carrierIds() {
        expectCarrier("kr.lotte");
        expectCarrier("kr.hanjin");
        expectCarrier("kr.logen");
        expectCarrier("kr.epost");

        client.track(Courier.LOTTE, "1");
        client.track(Courier.HANJIN, "1");
        client.track(Courier.LOGEN, "1");
        client.track(Courier.POST, "1");

        server.verify();
    }

    @Test
    @DisplayName("송장이 아직 택배사에 없으면(errors NOT_FOUND) ORDER_014")
    void notFound() {
        server.expect(requestTo(API_URL)).andRespond(withSuccess("""
                {"data": {"track": null}, "errors": [{"message": "Not Found", "extensions": {"code": "NOT_FOUND"}}]}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.track(Courier.CJ, "999"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OrderErrorCode.TRACKING_NOT_FOUND);
    }

    @Test
    @DisplayName("인증 실패 같은 다른 GraphQL 오류는 ORDER_015")
    void unauthenticated() {
        server.expect(requestTo(API_URL)).andRespond(withSuccess("""
                {"errors": [{"message": "Unauthenticated", "extensions": {"code": "UNAUTHENTICATED"}}]}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.track(Courier.CJ, "1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OrderErrorCode.TRACKING_PROVIDER_ERROR);
    }

    @Test
    @DisplayName("서버 오류·타임아웃은 ORDER_015")
    void serverErrorAndTimeout() {
        server.expect(requestTo(API_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(requestTo(API_URL)).andRespond(withException(new java.net.SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.track(Courier.CJ, "1"))
                .extracting("errorCode").isEqualTo(OrderErrorCode.TRACKING_PROVIDER_ERROR);
        assertThatThrownBy(() -> client.track(Courier.CJ, "1"))
                .hasCauseInstanceOf(ResourceAccessException.class)
                .extracting("errorCode").isEqualTo(OrderErrorCode.TRACKING_PROVIDER_ERROR);
    }

    @Test
    @DisplayName("기타 택배사는 조회하지 않고 ORDER_013, 키가 없으면 부르지 않고 ORDER_016")
    void unsupportedAndNotConfigured() {
        assertThat(DeliveryTrackingClient.supports(Courier.ETC)).isFalse();
        assertThatThrownBy(() -> client.track(Courier.ETC, "1"))
                .extracting("errorCode").isEqualTo(OrderErrorCode.TRACKING_UNSUPPORTED_COURIER);

        DeliveryTrackingClient notConfigured = new DeliveryTrackingClient(RestClient.builder().build(),
                new DeliveryTrackerProperties("", "", null, null, null));
        assertThatThrownBy(() -> notConfigured.track(Courier.CJ, "1"))
                .extracting("errorCode").isEqualTo(OrderErrorCode.TRACKING_NOT_CONFIGURED);
    }

    private void expectCarrier(String carrierId) {
        server.expect(requestTo(API_URL))
                .andExpect(jsonPath("$.variables.carrierId").value(carrierId))
                .andRespond(withSuccess("{\"data\": {\"track\": {\"lastEvent\": null, \"events\": {\"edges\": []}}}}",
                        MediaType.APPLICATION_JSON));
    }
}
