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