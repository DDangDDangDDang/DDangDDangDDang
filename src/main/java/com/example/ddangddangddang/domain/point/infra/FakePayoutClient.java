package com.example.ddangddangddang.domain.point.infra;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 실제 송금사가 정해지기 전까지 쓰는 임시 구현. 실제로 돈을 보내지 않는다.
 *
 * point.payout.provider=fake 일 때만 등록된다. 설정이 없으면 PayoutClient 빈이 없어서 앱이 뜨지 않는데,
 * 실수로 운영 환경에서 가짜 송금이 돌아가는 것(포인트는 차감되고 돈은 안 나가는 상황)을 막기 위해서다.
 *
 * point.payout.fake.result 로 결과를 바꿔 실패·복구 흐름을 직접 확인할 수 있다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "point.payout", name = "provider", havingValue = "fake")
public class FakePayoutClient implements PayoutClient {

    private final PayoutStatus configuredResult;

    /** payoutId별 결과. 같은 payoutId로 다시 요청하면 처음 결과를 돌려준다 (멱등성 흉내) */
    private final Map<String, PayoutResult> payouts = new ConcurrentHashMap<>();

    public FakePayoutClient(@Value("${point.payout.fake.result:SUCCESS}") PayoutStatus configuredResult) {
        this.configuredResult = configuredResult;
    }

    @PostConstruct
    void warn() {
        log.warn("[FakePayoutClient] 가짜 송금 구현이 활성화되어 있습니다. 실제 송금은 일어나지 않습니다. result={}",
                configuredResult);
    }

    @Override
    public PayoutResult requestPayout(PayoutRequest request) {
        PayoutResult result = payouts.computeIfAbsent(request.payoutId(), id -> toResult());
        log.info("[FakePayoutClient] 송금 요청 payoutId={}, amount={}, result={}",
                request.payoutId(), request.amount(), result.status());
        return result;
    }

    @Override
    public PayoutResult getPayout(String payoutId) {
        return payouts.getOrDefault(payoutId, PayoutResult.notFound());
    }

    private PayoutResult toResult() {
        return switch (configuredResult) {
            case SUCCESS -> PayoutResult.success();
            case FAILED -> PayoutResult.failed("가짜 송금 실패");
            case IN_PROGRESS, NOT_FOUND -> PayoutResult.inProgress();
        };
    }
}
