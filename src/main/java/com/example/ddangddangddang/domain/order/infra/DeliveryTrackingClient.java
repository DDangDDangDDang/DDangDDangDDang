package com.example.ddangddangddang.domain.order.infra;

import com.example.ddangddangddang.domain.order.entity.Courier;
import com.example.ddangddangddang.domain.order.exception.OrderErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Delivery Tracker(tracker.delivery) 배송 조회 클라이언트. 국내 택배사를 한 API(GraphQL)로 조회한다.
 * (https://tracker.delivery/docs/tracking-api)
 *
 * GraphQL은 조회 실패도 HTTP 200에 errors 배열로 알려준다. 송장이 아직 택배사에 등록되지 않았으면 NOT_FOUND가 온다.
 */
@Slf4j
@Component
public class DeliveryTrackingClient {

    /** 우리 택배사 → Delivery Tracker carrierId. ETC는 조회하지 않는다. */
    private static final Map<Courier, String> CARRIER_IDS = new EnumMap<>(Map.of(
            Courier.CJ, "kr.cjlogistics",
            Courier.LOTTE, "kr.lotte",
            Courier.HANJIN, "kr.hanjin",
            Courier.LOGEN, "kr.logen",
            Courier.POST, "kr.epost"
    ));

    /** 이벤트는 최근 20개만 받는다. (오래된 순으로 온다) */
    static final String TRACK_QUERY = """
            query Track($carrierId: ID!, $trackingNumber: String!) {
              track(carrierId: $carrierId, trackingNumber: $trackingNumber) {
                lastEvent { time status { code name } description location { name } }
                events(last: 20) { edges { node { time status { code name } description location { name } } } }
              }
            }
            """;

    private final RestClient restClient;
    private final DeliveryTrackerProperties properties;

    @Autowired
    public DeliveryTrackingClient(DeliveryTrackerProperties properties) {
        this(createRestClient(properties), properties);
    }

    DeliveryTrackingClient(RestClient restClient, DeliveryTrackerProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    private static RestClient createRestClient(DeliveryTrackerProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    public static boolean supports(Courier courier) {
        return CARRIER_IDS.containsKey(courier);
    }

    public TrackingInfo track(Courier courier, String trackingNo) {
        String carrierId = CARRIER_IDS.get(courier);
        if (carrierId == null) {
            throw new BusinessException(OrderErrorCode.TRACKING_UNSUPPORTED_COURIER);
        }
        if (!properties.isConfigured()) {
            throw new BusinessException(OrderErrorCode.TRACKING_NOT_CONFIGURED);
        }

        JsonNode response;
        try {
            response = restClient.post()
                    .uri(properties.apiUrl())
                    .header(HttpHeaders.AUTHORIZATION,
                            "TRACKQL-API-KEY " + properties.clientId() + ":" + properties.clientSecret())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("query", TRACK_QUERY,
                            "variables", Map.of("carrierId", carrierId, "trackingNumber", trackingNo)))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            // 4xx·5xx·타임아웃(ResourceAccessException) 모두. 키가 만료됐을 때도 여기로 온다
            log.error("배송 조회 요청 실패: courier={}", courier, e);
            throw new BusinessException(OrderErrorCode.TRACKING_PROVIDER_ERROR, e);
        }
        if (response == null) {
            throw new BusinessException(OrderErrorCode.TRACKING_PROVIDER_ERROR);
        }

        checkErrors(response, courier);
        JsonNode track = response.path("data").path("track");
        if (track.isMissingNode() || track.isNull()) {
            throw new BusinessException(OrderErrorCode.TRACKING_NOT_FOUND);
        }
        return toTrackingInfo(track);
    }

    /** NOT_FOUND·BAD_REQUEST(잘못된 송장)는 "아직 배송 정보 없음", 그 밖(인증 실패 등)은 서비스 장애로 본다. */
    private void checkErrors(JsonNode response, Courier courier) {
        JsonNode errors = response.path("errors");
        if (!errors.isArray() || errors.isEmpty()) {
            return;
        }
        String code = errors.get(0).path("extensions").path("code").asString("");
        if ("NOT_FOUND".equals(code) || "BAD_REQUEST".equals(code)) {
            throw new BusinessException(OrderErrorCode.TRACKING_NOT_FOUND);
        }
        log.error("배송 조회 오류 응답: courier={}, code={}, message={}",
                courier, code, errors.get(0).path("message").asString(""));
        throw new BusinessException(OrderErrorCode.TRACKING_PROVIDER_ERROR);
    }

    private TrackingInfo toTrackingInfo(JsonNode track) {
        List<TrackingEvent> events = new ArrayList<>();
        for (JsonNode edge : track.path("events").path("edges")) {
            events.add(toEvent(edge.path("node")));
        }
        JsonNode last = track.path("lastEvent");
        TrackingEvent lastEvent = last.isObject() ? toEvent(last) : (events.isEmpty() ? null : events.get(events.size() - 1));
        return new TrackingInfo(lastEvent, events);
    }

    private TrackingEvent toEvent(JsonNode node) {
        return new TrackingEvent(
                node.path("time").asString(null),
                node.path("status").path("code").asString("UNKNOWN"),
                node.path("status").path("name").asString(null),
                node.path("location").path("name").asString(null),
                node.path("description").asString(null));
    }
}