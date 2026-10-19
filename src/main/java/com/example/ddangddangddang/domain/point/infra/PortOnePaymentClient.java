package com.example.ddangddangddang.domain.point.infra;

import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * 포트원 V2 REST API 클라이언트.
 *
 * 충전 확정은 충전 요청 행에 락을 잡은 채로 이 클라이언트를 호출한다.
 * 그래서 타임아웃이 꼭 있어야 한다. 없으면 포트원이 응답하지 않을 때 락과 DB 커넥션을 무한정 붙잡는다.
 * 타임아웃이 나면 PAYMENT_GATEWAY_ERROR로 롤백되고, 충전 요청은 PENDING으로 남아 다시 처리할 수 있다.
 */
@Slf4j
@Component
public class PortOnePaymentClient implements PaymentGateway {

    private final RestClient restClient;

    public PortOnePaymentClient(PortOneProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        this.restClient = RestClient.builder()
                .baseUrl(properties.apiUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "PortOne " + properties.apiSecret())
                .build();
    }

    @Override
    public PaymentInfo getPayment(String paymentId) {
        try {
            PortOnePaymentResponse response = restClient.get()
                    .uri("/payments/{paymentId}", paymentId)
                    .retrieve()
                    .body(PortOnePaymentResponse.class);

            if (response == null) {
                throw new BusinessException(PointErrorCode.PAYMENT_GATEWAY_ERROR);
            }
            return response.toPaymentInfo();

        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw new BusinessException(PointErrorCode.PAYMENT_NOT_FOUND, e);
            }
            log.error("포트원 결제 조회 실패 paymentId={}, status={}, body={}",
                    paymentId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(PointErrorCode.PAYMENT_GATEWAY_ERROR, e);
        } catch (RestClientException e) {
            // 타임아웃도 여기로 온다 (ResourceAccessException)
            log.error("포트원 결제 조회 통신 오류 paymentId={}", paymentId, e);
            throw new BusinessException(PointErrorCode.PAYMENT_GATEWAY_ERROR, e);
        }
    }

    @Override
    public void cancelPayment(String paymentId, String reason) {
        try {
            restClient.post()
                    .uri("/payments/{paymentId}/cancel", paymentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("reason", reason))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("포트원 결제 취소 실패 paymentId={}", paymentId, e);
            throw new BusinessException(PointErrorCode.PAYMENT_GATEWAY_ERROR, e);
        }
    }

    /** 포트원 응답 중 필요한 필드만 받는다. 나머지 필드는 무시. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PortOnePaymentResponse(String id, String status, Amount amount) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Amount(long total) {
        }

        PaymentInfo toPaymentInfo() {
            long total = amount == null ? 0L : amount.total();
            return new PaymentInfo(id, PaymentStatus.from(status), total);
        }
    }
}
