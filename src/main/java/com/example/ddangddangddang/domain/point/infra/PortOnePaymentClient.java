package com.example.ddangddangddang.domain.point.infra;

import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Slf4j
@Component
public class PortOnePaymentClient implements PaymentClient {

    private final RestClient restClient;

    public PortOnePaymentClient(PortOneProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.apiUrl())
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
