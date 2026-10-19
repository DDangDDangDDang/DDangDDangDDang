package com.example.ddangddangddang.domain.point.controller;

import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.domain.point.infra.PortOneWebhookPayload;
import com.example.ddangddangddang.domain.point.infra.PortOneWebhookVerifier;
import com.example.ddangddangddang.domain.point.service.PointChargeService;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * 포트원 웹훅 수신. 포트원 콘솔의 웹훅 URL에 이 주소를 등록한다.
 *
 * 응답 규칙:
 * - 2xx: 처리 완료 또는 무시해도 되는 웹훅 → 포트원이 재전송하지 않는다
 * - 4xx: 서명 불일치, 본문 오류 → 잘못된 요청
 * - 5xx: 처리 중 오류 (포트원 API 장애, 지갑 동시 수정 충돌 등) → 포트원이 재전송한다
 *
 * 로그인이 없는 요청이라 SecurityConfig에서 이 경로를 permitAll로 열어야 한다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/points/charges")
public class PortOneWebhookController {

    private final PortOneWebhookVerifier webhookVerifier;
    private final PointChargeService pointChargeService;
    private final ObjectMapper objectMapper;

    /**
     * body는 반드시 String으로 받는다. DTO로 바로 받으면 Spring이 JSON을 다시 직렬화하면서
     * 원본과 글자가 달라질 수 있고, 그러면 서명 검증이 실패한다.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> receive(
            @RequestBody String body,
            @RequestHeader("webhook-id") String webhookId,
            @RequestHeader("webhook-timestamp") String webhookTimestamp,
            @RequestHeader("webhook-signature") String webhookSignature) {

        webhookVerifier.verify(body, webhookId, webhookTimestamp, webhookSignature);

        PortOneWebhookPayload payload = parse(body);
        if (!payload.isTransactionEvent()) {
            log.info("처리 대상이 아닌 포트원 웹훅 webhookId={}, type={}", webhookId, payload.type());
            return ResponseEntity.ok().build();
        }

        log.info("포트원 웹훅 수신 webhookId={}, type={}, paymentId={}", webhookId, payload.type(), payload.paymentId());
        pointChargeService.syncChargeFromWebhook(payload.paymentId());
        return ResponseEntity.ok().build();
    }

    private PortOneWebhookPayload parse(String body) {
        try {
            return objectMapper.readValue(body, PortOneWebhookPayload.class);
        } catch (Exception e) {
            throw new BusinessException(PointErrorCode.INVALID_WEBHOOK_PAYLOAD, e);
        }
    }
}
