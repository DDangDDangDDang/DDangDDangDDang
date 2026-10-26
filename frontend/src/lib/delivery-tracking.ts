import type { Courier } from './labels';

/*
 * 배송 조회 (백엔드: GET /api/v1/orders/{orderId}/tracking, Delivery Tracker 연동)
 * 상태 코드는 택배사와 관계없이 통일된 값이다.
 */

export type TrackingStatusCode =
    | 'INFORMATION_RECEIVED'
    | 'AT_PICKUP'
    | 'IN_TRANSIT'
    | 'OUT_FOR_DELIVERY'
    | 'ATTEMPT_FAIL'
    | 'DELIVERED'
    | 'AVAILABLE_FOR_PICKUP'
    | 'EXCEPTION'
    | 'UNKNOWN';

const STATUS_LABEL: Record<TrackingStatusCode, string> = {
    INFORMATION_RECEIVED: '접수',
    AT_PICKUP: '집화',
    IN_TRANSIT: '배송 중',
    OUT_FOR_DELIVERY: '배달 출발',
    ATTEMPT_FAIL: '배달 실패',
    DELIVERED: '배달 완료',
    AVAILABLE_FOR_PICKUP: '보관 중',
    EXCEPTION: '배송 문제',
    UNKNOWN: '확인 중',
};

/** 상태 코드 → 화면 문구. 모르는 값이면 '확인 중' */
export function trackingStatusLabel(code: string | undefined): string {
    return STATUS_LABEL[code as TrackingStatusCode] ?? STATUS_LABEL.UNKNOWN;
}

/** 상태 배지 색: 배달 완료(info), 문제(warning), 그 밖(neutral) */
export function trackingStatusVariant(code: string | undefined): 'info' | 'warning' | 'neutral' {
    if (code === 'DELIVERED') return 'info';
    if (code === 'ATTEMPT_FAIL' || code === 'EXCEPTION') return 'warning';
    return 'neutral';
}

/** 배송 단계 그림: 접수 → 출발 → 배송 중 → 도착 */
export const TRACKING_STEPS = ['접수', '출발', '배송 중', '도착'] as const;

/** 상태 코드 → 단계 번호. 배달 출발·배달 실패·보관 중은 아직 받기 전이라 '배송 중'에 둔다 */
const STEP_INDEX: Partial<Record<TrackingStatusCode, number>> = {
    INFORMATION_RECEIVED: 0,
    AT_PICKUP: 1,
    IN_TRANSIT: 2,
    OUT_FOR_DELIVERY: 2,
    ATTEMPT_FAIL: 2,
    AVAILABLE_FOR_PICKUP: 2,
    DELIVERED: 3,
};

export interface TrackingStepsView {
    /** 지금 단계 번호 (0~3). 이력이 없으면 -1 */
    current: number;
    /** 단계별 택배사 상태 이름 (오래된 순). 예: 배송 중 → ['간선상차', '간선하차', '배송출발'] */
    details: string[][];
}

/**
 * 이력(오래된 순)을 단계로 묶는다.
 * 단계를 알 수 없는 상태(문제 발생, 확인 중)는 그때까지의 단계 아래에 적는다.
 */
export function trackingSteps(events: { statusCode?: string; statusName?: string }[]): TrackingStepsView {
    const details: string[][] = TRACKING_STEPS.map(() => []);
    let current = -1;
    for (const event of events) {
        const index = STEP_INDEX[event.statusCode as TrackingStatusCode] ?? Math.max(current, 0);
        current = Math.max(current, index);
        details[index]?.push(event.statusName ?? trackingStatusLabel(event.statusCode));
    }
    return { current, details };
}

/**
 * 택배사 공식 조회 페이지. 배송 조회 API가 실패하거나 지원하지 않을 때 대신 보여준다.
 * 택배사가 주소를 바꾸면 여기만 고친다. 기타(ETC)는 없음.
 */
const COURIER_TRACKING_URL: Partial<Record<Courier, (trackingNo: string) => string>> = {
    CJ: (no) => `https://trace.cjlogistics.com/next/tracking.html?wblNo=${encodeURIComponent(no)}`,
    LOTTE: (no) => `https://www.lotteglogis.com/home/reservation/tracking/linkView?InvNo=${encodeURIComponent(no)}`,
    HANJIN: (no) => `https://www.hanjin.com/kor/CMS/DeliveryMgr/WaybillResult.do?mCode=MN038&schLang=KR&wblnumText2=${encodeURIComponent(no)}`,
    LOGEN: (no) => `https://www.ilogen.com/web/personal/trace/${encodeURIComponent(no)}`,
    POST: (no) => `https://service.epost.go.kr/trace.RetrieveDomRigiTraceList.comm?sid1=${encodeURIComponent(no)}`,
};

export function courierTrackingUrl(courier: Courier | undefined, trackingNo: string | undefined): string | undefined {
    if (!courier || !trackingNo) return undefined;
    return COURIER_TRACKING_URL[courier]?.(trackingNo);
}