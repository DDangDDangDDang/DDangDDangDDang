import type { Schema } from '@/api';

/*
 * 판매자 프로필 화면의 계산 로직. (화면과 분리해 단위 테스트한다)
 */

type Trust = Schema<'Trust'>;

/** 방송한 시간 합(초)을 "12시간 30분"처럼. 1분이 안 되면 "1분 미만", 방송한 적 없으면 "0분" */
export function formatBroadcastDuration(totalSeconds: number): string {
  if (totalSeconds <= 0) return '0분';
  const minutes = Math.floor(totalSeconds / 60);
  if (minutes === 0) return '1분 미만';
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) return `${rest}분`;
  return rest === 0 ? `${hours}시간` : `${hours}시간 ${rest}분`;
}

/**
 * 낙찰률(%): 마감된 경매(낙찰 + 유찰) 중 낙찰된 비율. 판매자가 취소한 경매는 빼고 센다.
 * 마감된 경매가 없으면 undefined (아직 판단할 자료가 없음)
 */
export function soldRate(trust?: Trust): number | undefined {
  const sold = trust?.soldAuctionCount ?? 0;
  const closed = sold + (trust?.failedAuctionCount ?? 0);
  return closed === 0 ? undefined : Math.round((sold / closed) * 100);
}

/** 평균 발송 시간: 24시간 미만은 "5.5시간", 넘으면 "1.5일". 발송한 적 없으면 undefined */
export function formatShippingTime(hours?: number): string | undefined {
  if (hours === undefined || hours === null) return undefined;
  if (hours < 24) return `${hours}시간`;
  return `${Math.round((hours / 24) * 10) / 10}일`;
}
