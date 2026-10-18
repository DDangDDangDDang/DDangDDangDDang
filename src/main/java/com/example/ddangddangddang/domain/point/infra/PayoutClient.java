package com.example.ddangddangddang.domain.point.infra;

/**
 * 송금(지급대행) 추상화. 실제 송금사는 아직 정해지지 않았다.
 * 송금사가 정해지면 이 인터페이스의 구현체만 추가하고, 설정(point.payout.provider)으로 바꿔 끼운다.
 *
 * 구현체가 반드시 지켜야 할 규칙:
 * 1. payoutId가 같은 요청은 몇 번을 보내도 송금이 한 번만 일어나야 한다 (멱등성).
 * 2. 타임아웃, 네트워크 오류처럼 결과를 알 수 없으면 FAILED가 아니라 IN_PROGRESS를 돌려준다.
 *    FAILED를 돌려주면 포인트가 환불되는데, 실제로는 송금이 됐을 수 있기 때문이다.
 */
public interface PayoutClient {

    /** 송금 요청 */
    PayoutResult requestPayout(PayoutRequest request);

    /** 송금 결과 조회. 송금사에 요청 기록이 없으면 NOT_FOUND */
    PayoutResult getPayout(String payoutId);
}
