import { describe, expect, it } from 'vitest';
import { courierTrackingUrl, trackingStatusLabel, trackingStatusVariant, trackingSteps } from './delivery-tracking';

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

  it('이력을 접수·출발·배송 중·도착 단계로 묶고, 가장 많이 나아간 단계가 지금 단계다', () => {
    const view = trackingSteps([
      { statusCode: 'INFORMATION_RECEIVED', statusName: '접수' },
      { statusCode: 'AT_PICKUP', statusName: '집화처리' },
      { statusCode: 'IN_TRANSIT', statusName: '간선상차' },
      { statusCode: 'IN_TRANSIT', statusName: '간선하차' },
      { statusCode: 'OUT_FOR_DELIVERY', statusName: '배송출발' },
    ]);

    expect(view.current).toBe(2);
    expect(view.details).toEqual([['접수'], ['집화처리'], ['간선상차', '간선하차', '배송출발'], []]);
  });

  it('배달 완료면 도착 단계, 단계를 알 수 없는 상태는 그때 단계 아래에 적고, 이력이 없으면 -1', () => {
    expect(trackingSteps([{ statusCode: 'AT_PICKUP', statusName: '집화' }, { statusCode: 'DELIVERED', statusName: '배달완료' }]).current).toBe(3);
    expect(trackingSteps([{ statusCode: 'IN_TRANSIT', statusName: '간선상차' }, { statusCode: 'EXCEPTION' }]).details[2]).toEqual(['간선상차', '배송 문제']);
    expect(trackingSteps([{ statusCode: 'UNKNOWN', statusName: '기타' }]).details[0]).toEqual(['기타']);
    expect(trackingSteps([]).current).toBe(-1);
  });
});
