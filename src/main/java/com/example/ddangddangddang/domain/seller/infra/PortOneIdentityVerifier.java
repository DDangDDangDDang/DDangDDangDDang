package com.example.ddangddangddang.domain.seller.infra;

import com.example.ddangddangddang.domain.point.infra.PortOneProperties;
import com.example.ddangddangddang.domain.seller.exception.SellerErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 포트원 V2 본인인증 조회 (GET /identity-verifications/{id}). 결제와 같은 API Secret·상점 아이디를 쓴다.
 * 본인인증 채널(다날 등)은 화면 쪽 채널 키(VITE_PORTONE_IDENTITY_CHANNEL_KEY)로 고른다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "seller.identity", name = "provider", havingValue = "portone", matchIfMissing = true)
public class PortOneIdentityVerifier implements IdentityVerifier {

    private static final String VERIFIED = "VERIFIED";

    private final RestClient restClient;
    private final String storeId;

    public PortOneIdentityVerifier(PortOneProperties properties) {
        this.storeId = properties.storeId();

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
    public VerifiedIdentity verify(String identityVerificationId) {
        PortOneIdentityVerification response;
        try {
            response = restClient.get()
                    .uri(builder -> {
                        builder.path("/identity-verifications/{identityVerificationId}");
                        if (storeId != null) {
                            builder.queryParam("storeId", storeId);
                        }
                        return builder.build(identityVerificationId);
                    })
                    .retrieve()
                    .body(PortOneIdentityVerification.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                log.warn("포트원에 본인인증 없음 identityVerificationId={}, storeId={}", identityVerificationId, storeId);
                throw new BusinessException(SellerErrorCode.IDENTITY_NOT_VERIFIED, e);
            }
            log.error("포트원 본인인증 조회 실패 identityVerificationId={}, status={}, body={}",
                    identityVerificationId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(SellerErrorCode.IDENTITY_GATEWAY_ERROR, e);
        } catch (RestClientException e) {
            log.error("포트원 본인인증 조회 통신 오류 identityVerificationId={}", identityVerificationId, e);
            throw new BusinessException(SellerErrorCode.IDENTITY_GATEWAY_ERROR, e);
        }
        return toVerifiedIdentity(identityVerificationId, response);
    }

    static VerifiedIdentity toVerifiedIdentity(String identityVerificationId, PortOneIdentityVerification response) {
        if (response == null || !VERIFIED.equals(response.status()) || response.verifiedCustomer() == null) {
            throw new BusinessException(SellerErrorCode.IDENTITY_NOT_VERIFIED);
        }
        Customer customer = response.verifiedCustomer();
        if (isBlank(customer.name()) || isBlank(customer.birthDate()) || isBlank(customer.ci())) {
            // 본인인증 채널 설정에 따라 CI·생년월일이 빠질 수 있다. 빠지면 미성년자·중복 확인을 할 수 없어 받지 않는다.
            log.error("포트원 본인인증 결과에 이름·생년월일·CI가 없음 identityVerificationId={}", identityVerificationId);
            throw new BusinessException(SellerErrorCode.IDENTITY_GATEWAY_ERROR);
        }
        try {
            return new VerifiedIdentity(customer.name().strip(), LocalDate.parse(customer.birthDate()), customer.ci());
        } catch (DateTimeParseException e) {
            log.error("포트원 본인인증 생년월일 형식 오류 identityVerificationId={}", identityVerificationId);
            throw new BusinessException(SellerErrorCode.IDENTITY_GATEWAY_ERROR, e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 포트원 응답 중 필요한 필드만 받는다. birthDate는 yyyy-MM-dd */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PortOneIdentityVerification(String id, String status, Customer verifiedCustomer) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Customer(String name, String birthDate, String ci) {
    }
}
