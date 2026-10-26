import { describe, expect, it } from 'vitest';
import { courierTrackingUrl, trackingStatusLabel, trackingStatusVariant } from './delivery-tracking';

describe('delivery-tracking', () => {
  it('상태 코드를 화면 문구로 바꾸고, 모르는 값은 확인 중으로 본다', () => {
    expect(trackingStatusLabel('IN_TRANSIT')).toBe('배송 중');
    expect(trackingStatusLabel('DELIVERED')).toBe('배달 완료');
    expect(trackingStatusLabel('SOMETHING_NEW')).toBe('확인 중');
    expect(trackingStatusLabel(undefined)).toBe('확인 중');
  });

  it('배달 완료는 info, 배달 실패·문제는 warning 배지', () => {
    expect(trackingStatusVariant('DELIVERED')).toBe('info');
    expect(trackingStatusVariant('ATTEMPT_FAIL')).toBe('warning');
    expect(trackingStatusVariant('EXCEPTION')).toBe('warning');
    expect(trackingStatusVariant('IN_TRANSIT')).toBe('neutral');
  });

  it('택배사 조회 페이지 주소에 송장번호를 넣고, 기타 택배사·송장 없음은 주소가 없다', () => {
    expect(courierTrackingUrl('CJ', '1234567890')).toBe('https://trace.cjlogistics.com/next/tracking.html?wblNo=1234567890');
    expect(courierTrackingUrl('POST', '1234')).toContain('sid1=1234');
    expect(courierTrackingUrl('ETC', '1234')).toBeUndefined();
    expect(courierTrackingUrl('CJ', undefined)).toBeUndefined();
  });
});
