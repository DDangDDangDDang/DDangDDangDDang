import type { paths, Schema } from '@/api';

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

const BUSINESS_NUMBER_WEIGHTS = [1, 3, 7, 1, 3, 7, 1, 3, 5];

/** 사업자등록번호 입력에서 숫자만 남긴다. (하이픈·공백 허용) */
export function businessNumberDigits(input: string): string {
  return input.replace(/[\s-]/g, '');
}

/** 사업자등록번호 10자리의 검증 숫자가 맞는지 (서버 BusinessNumber와 같은 국세청 규칙) */
export function isValidBusinessNumber(input: string): boolean {
  const digits = businessNumberDigits(input);
  if (!/^\d{10}$/.test(digits)) return false;
  const nums = [...digits].map(Number);
  let sum = BUSINESS_NUMBER_WEIGHTS.reduce((acc, w, i) => acc + (nums[i] ?? 0) * w, 0);
  sum += Math.floor(((nums[8] ?? 0) * 5) / 10);
  return (10 - (sum % 10)) % 10 === nums[9];
}

/** 숫자 10자리를 "123-45-67890"으로 */
export function formatBusinessNumber(digits: string): string {
  return digits.length === 10 ? `${digits.slice(0, 3)}-${digits.slice(3, 5)}-${digits.slice(5)}` : digits;
}

/** 평균 발송 시간: 24시간 미만은 "5.5시간", 넘으면 "1.5일". 발송한 적 없으면 undefined */
export function formatShippingTime(hours?: number): string | undefined {
  if (hours === undefined || hours === null) return undefined;
  if (hours < 24) return `${hours}시간`;
  return `${Math.round((hours / 24) * 10) / 10}일`;
}

/** 상점 경매 기록 조회 조건 (명세에서 생성된 타입) */
type SellerAuctionQueryParams = NonNullable<paths['/api/v1/sellers/{sellerId}/auctions']['get']['parameters']['query']>;

/** 상점 경매 기록의 탭: 진행 중(기본) / 시작 전 / 지난 경매 */
export type SellerAuctionTab = NonNullable<SellerAuctionQueryParams['tab']>;

export const SELLER_AUCTION_TABS: { key: SellerAuctionTab; label: string }[] = [
  { key: 'ONGOING', label: '진행 중' },
  { key: 'READY', label: '시작 전' },
  { key: 'PAST', label: '지난 경매' },
];

/**
 * 상점 경매 기록 조회 조건 (GET /sellers/{sellerId}/auctions). 지난 경매만 유찰·취소 포함 여부를 보낸다.
 * (서버 기본은 진행 중, 지난 경매는 낙찰만)
 */
export function sellerAuctionQuery(tab: SellerAuctionTab, includeFailedAndCanceled: boolean): Pick<SellerAuctionQueryParams, 'tab' | 'includeFailedAndCanceled'> {
  return tab === 'PAST' ? { tab, includeFailedAndCanceled } : { tab };
}

/** 경매 기록 한 줄의 가격 이름. 낙찰은 낙찰가, 진행 중은 현재가, 시작 전·유찰·취소는 시작가 */
export function sellerAuctionPriceLabel(status?: Schema<'SellerAuctionResponse'>['status']): '낙찰가' | '현재가' | '시작가' {
  if (status === 'ENDED') return '낙찰가';
  if (status === 'ONGOING') return '현재가';
  return '시작가';
}

/** 탭에 경매가 없을 때의 안내 */
export function sellerAuctionEmpty(tab: SellerAuctionTab, includeFailedAndCanceled: boolean): { title: string; description: string } {
  if (tab === 'ONGOING') return { title: '진행 중인 경매가 없어요', description: '판매자가 경매를 열면 여기에 나와요.' };
  if (tab === 'READY') return { title: '시작을 기다리는 경매가 없어요', description: '시작 시각을 정해 둔 경매가 여기에 나와요.' };
  return includeFailedAndCanceled
    ? { title: '지난 경매가 없어요', description: '끝난 경매(낙찰·유찰·취소)가 여기에 쌓여요.' }
    : { title: '낙찰된 경매가 없어요', description: '유찰·취소된 경매는 "유찰·취소 포함"을 켜면 보여요.' };
}
