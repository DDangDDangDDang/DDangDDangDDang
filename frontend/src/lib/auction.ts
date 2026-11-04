import type { Schema } from '@/api';
import { fromDateTimeInput } from './datetime-input';
import type { AuctionStatus } from './labels';

/*
 * 경매·상품 화면이 같이 쓰는 계산 로직. (화면과 분리해 단위 테스트한다)
 */

type Category = Schema<'CategoryResponse'>;

/**
 * 화면에 보여줄 경매 단계. 서버는 마감 시각이 지나도 결과를 처리하기 전까지 ONGOING으로 두므로,
 * 진행 중인데 마감 시각이 지났으면 CLOSING(마감, 결과 집계 중)으로 본다.
 */
export type AuctionPhase = AuctionStatus | 'CLOSING';

export const AUCTION_PHASE_LABEL: Record<AuctionPhase, string> = {
  READY: '시작 전',
  ONGOING: '진행 중',
  CLOSING: '마감',
  ENDED: '낙찰',
  FAILED: '유찰',
  CANCELED: '취소됨',
};

export function auctionPhase(auction: { status?: AuctionStatus; endAt?: string }, now: Date): AuctionPhase | undefined {
  if (!auction.status) return undefined;
  if (auction.status === 'ONGOING' && auction.endAt && new Date(auction.endAt) <= now) return 'CLOSING';
  return auction.status;
}

/** 판매자 스튜디오 경매 목록의 상태 탭 */
export type AuctionStatusTab = 'ONGOING' | 'READY' | 'ENDED';

/** 진행 중·시작 전이 아니면(마감 집계 중·낙찰·유찰·취소) 모두 종료 탭에 모은다. */
export function auctionStatusTab(phase: AuctionPhase | undefined): AuctionStatusTab {
  return phase === 'ONGOING' || phase === 'READY' ? phase : 'ENDED';
}

/** 영상 위에 한 번에 띄울 수 있는 경매 수 (서버도 넘으면 LIVESTREAM_017로 막는다) */
export const MAX_FEATURED_AUCTIONS = 2;

/**
 * 판매자가 영상 위에 띄운 경매들, 띄운 순서대로. (방송 정보의 featuredAuctionIds를 방송 경매 목록에서 찾는다)
 * 시작 전·진행 중(마감 집계 중 포함)일 때만 띄우고, 끝나거나 취소되면 판매자가 내리지 않아도 보이지 않는다.
 * 서버도 끝난 경매는 자리를 차지하지 않는 것으로 보므로, 이 목록의 개수로 더 띄울 수 있는지 판단한다.
 */
export function featuredAuctions<T extends { id?: number; status?: AuctionStatus; endAt?: string }>(
  featuredAuctionIds: number[] | undefined,
  auctions: T[],
  now: Date,
): T[] {
  return (featuredAuctionIds ?? []).flatMap((id) => {
    const auction = auctions.find((a) => a.id === id);
    if (!auction) return [];
    const phase = auctionPhase(auction, now);
    return phase === 'READY' || phase === 'ONGOING' || phase === 'CLOSING' ? [auction] : [];
  });
}

/** 서버 시각까지 남은 초 (지났으면 0) */
export function secondsUntil(serverTime: string | undefined, now: Date): number {
  if (!serverTime) return 0;
  return Math.max(0, Math.floor((new Date(serverTime).getTime() - now.getTime()) / 1000));
}

/** 고른 카테고리 id가 속한 대분류. 대분류 id면 그 대분류, 비었거나 없는 id면 undefined */
export function parentCategoryOf(categories: Category[], id: string): Category | undefined {
  if (!id) return undefined;
  return categories.find((parent) => String(parent.id) === id || (parent.children ?? []).some((child) => String(child.id) === id));
}

/** 1차(대분류) 선택지. leafOnly면 소분류가 없는 대분류는 뺀다. 상품은 소분류에만 등록한다. (PRODUCT_003) */
export function parentCategoryOptions(categories: Category[], leafOnly = false): { value: string; label: string }[] {
  return categories
    .filter((parent) => parent.id !== undefined && (!leafOnly || (parent.children ?? []).length > 0))
    .map((parent) => ({ value: String(parent.id), label: parent.name ?? '' }));
}

/** 2차(소분류) 선택지 */
export function childCategoryOptions(parent: Category | undefined): { value: string; label: string }[] {
  return (parent?.children ?? []).filter((child) => child.id !== undefined).map((child) => ({ value: String(child.id), label: child.name ?? '' }));
}

/** 금액 입력에서 숫자만 꺼낸다. 비었거나 숫자가 없으면 undefined */
export function parseAmount(value: string): number | undefined {
  const digits = value.replace(/\D/g, '');
  return digits ? Number(digits) : undefined;
}

const MINUTE = 60;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/**
 * 방송 중 경매 진행 시간 (서버: 30초 ~ 3일, LiveAuctionCreateRequest.durationSec)
 * 방송이 끝나도 경매는 정한 시간까지 이어진다.
 */
export const LIVE_AUCTION_DURATIONS = [
  30,
  MINUTE,
  2 * MINUTE,
  3 * MINUTE,
  5 * MINUTE,
  10 * MINUTE,
  30 * MINUTE,
  HOUR,
  3 * HOUR,
  6 * HOUR,
  12 * HOUR,
  DAY,
  2 * DAY,
  3 * DAY,
] as const;

/** 진행 시간을 "30초", "5분", "3시간", "2일"처럼 */
export function durationLabel(seconds: number): string {
  if (seconds < MINUTE) return `${seconds}초`;
  if (seconds < HOUR) return `${seconds / MINUTE}분`;
  if (seconds < DAY) return `${seconds / HOUR}시간`;
  return `${seconds / DAY}일`;
}

/** 경매 금액(시작가·입찰 단위·즉시구매가) 상한. 서버 Auction.MAX_PRICE와 같다 */
export const MAX_AUCTION_PRICE = 100_000_000;
/** 일반 경매(시작·마감 시각을 정하는 경매) 진행 시간 범위와 시작 시각 상한. 서버 Auction.MIN_DURATION·MAX_DURATION·MAX_START_AHEAD와 같다 */
export const MIN_SCHEDULED_DURATION_MS = MINUTE * 1000;
export const MAX_SCHEDULED_DURATION_MS = 14 * DAY * 1000;
export const MAX_START_AHEAD_MS = 30 * DAY * 1000;

/** 경매 조건 입력. 금액은 입력 문자열, 시각은 datetime-local 값 */
export type AuctionConditionValues = {
  /** 즉시구매만 판매: 켜면 시작가·최소 입찰 단위 없이 즉시구매가로만 판다 (서버에는 두 값을 빼고 보낸다) */
  buyNowOnly: boolean;
  startPrice: string;
  bidUnit: string;
  buyNowPrice: string;
  startAt: string;
  endAt: string;
  durationSec: string;
};
export type AuctionConditionErrors = Partial<Record<keyof AuctionConditionValues, string>>;

export const EMPTY_AUCTION_CONDITION: AuctionConditionValues = { buyNowOnly: false, startPrice: '', bidUnit: '', buyNowPrice: '', startAt: '', endAt: '', durationSec: '60' };

/**
 * 경매 조건을 검사한다. schedule은 시작·마감 시각을 정하는 경매, live는 방송 중에 바로 시작하는 경매.
 * (서버도 같은 규칙으로 다시 검사한다: 금액 양수·1억 원 이하, 즉시구매가 > 시작가, 시각은 미래·마감 > 시작, 진행 1분~14일, 시작은 30일 이내)
 */
export function validateAuctionCondition(values: AuctionConditionValues, mode: 'schedule' | 'live', now: Date): AuctionConditionErrors {
  const errors: AuctionConditionErrors = {};
  const startPrice = parseAmount(values.startPrice);
  const buyNowPrice = parseAmount(values.buyNowPrice);

  // 즉시구매만 판매([즉시구매만 판매] 버튼)면 시작가·입찰 단위를 보지 않고 즉시구매가만 필수다. (서버: AuctionService.priceVerification)
  if (values.buyNowOnly) {
    if (buyNowPrice === undefined) errors.buyNowPrice = '즉시구매만 판매하려면 즉시구매가를 입력해 주세요.';
    else if (buyNowPrice === 0) errors.buyNowPrice = '즉시구매가는 0원일 수 없어요.';
    else if (buyNowPrice > MAX_AUCTION_PRICE) errors.buyNowPrice = tooHigh('즉시구매가');
  } else {
    const startError = amountError(values.startPrice, '시작가');
    const unitError = amountError(values.bidUnit, '최소 입찰 단위');
    if (startError) errors.startPrice = startError;
    if (unitError) errors.bidUnit = unitError;
    if (buyNowPrice !== undefined && buyNowPrice > MAX_AUCTION_PRICE) errors.buyNowPrice = tooHigh('즉시구매가');
    else if (buyNowPrice !== undefined && startPrice && buyNowPrice <= startPrice) errors.buyNowPrice = '즉시구매가는 시작가보다 커야 해요.';
  }

  if (mode === 'schedule') {
    const startAt = values.startAt ? new Date(values.startAt) : undefined;
    const endAt = values.endAt ? new Date(values.endAt) : undefined;
    if (!startAt) errors.startAt = '시작 시각을 정해 주세요.';
    else if (startAt <= now) errors.startAt = '시작 시각은 지금 이후로 정해 주세요.';
    else if (startAt.getTime() - now.getTime() > MAX_START_AHEAD_MS) errors.startAt = '시작 시각은 30일 이내로 정해 주세요.';
    if (!endAt) errors.endAt = '마감 시각을 정해 주세요.';
    else if (startAt && endAt <= startAt) errors.endAt = '마감 시각은 시작 시각보다 뒤여야 해요.';
    else if (startAt) {
      const duration = endAt.getTime() - startAt.getTime();
      if (duration < MIN_SCHEDULED_DURATION_MS || duration > MAX_SCHEDULED_DURATION_MS) errors.endAt = '경매는 1분 이상 14일 이내로 진행할 수 있어요.';
    }
  } else if (!LIVE_AUCTION_DURATIONS.includes(Number(values.durationSec) as (typeof LIVE_AUCTION_DURATIONS)[number])) {
    errors.durationSec = '진행 시간을 선택해 주세요.';
  }
  return errors;
}

export function toAuctionCreateRequest(productId: number, values: AuctionConditionValues): Schema<'AuctionCreateRequest'> {
  return {
    productId,
    // 즉시구매만 판매면 시작가·입찰 단위를 보내지 않는다 (서버에 즉시구매 전용으로 저장)
    startPrice: values.buyNowOnly ? undefined : parseAmount(values.startPrice),
    bidUnit: values.buyNowOnly ? undefined : parseAmount(values.bidUnit),
    buyNowPrice: parseAmount(values.buyNowPrice),
    startAt: fromDateTimeInput(values.startAt) ?? '',
    endAt: fromDateTimeInput(values.endAt) ?? '',
  };
}

export function toLiveAuctionCreateRequest(productId: number, values: AuctionConditionValues): Schema<'LiveAuctionCreateRequest'> {
  return {
    productId,
    startPrice: values.buyNowOnly ? undefined : parseAmount(values.startPrice),
    bidUnit: values.buyNowOnly ? undefined : parseAmount(values.bidUnit),
    buyNowPrice: parseAmount(values.buyNowPrice),
    durationSec: Number(values.durationSec),
  };
}

/** 시작가·최소 입찰 단위 입력 오류. 비었으면 "입력해 주세요", 0이면 "0원일 수 없어요" */
function amountError(raw: string, label: string): string | undefined {
  if (raw.trim() === '') return `${label}를 입력해 주세요.`;
  const amount = parseAmount(raw);
  if (amount === 0) return `${label}는 0원일 수 없어요.`;
  if (!amount) return `${label}를 입력해 주세요.`;
  if (amount > MAX_AUCTION_PRICE) return tooHigh(label);
  return undefined;
}

function tooHigh(label: string): string {
  return `${label}는 1억 원까지 정할 수 있어요.`;
}

/**
 * 가격 옆에 붙일 이름. 즉시구매 전용 경매(buyNowOnly)는 입찰이 없어 "현재가" 대신 즉시구매가, 팔렸으면 판매가.
 * 입찰 경매는 시작 전이면 시작가, 끝났으면 낙찰가, 그 밖에는 현재가.
 */
export function auctionPriceLabel(phase: AuctionPhase | undefined, buyNowOnly: boolean | undefined): string {
  if (buyNowOnly) return phase === 'ENDED' ? '판매가' : '즉시구매가';
  if (phase === 'READY') return '시작가';
  if (phase === 'ENDED') return '낙찰가';
  return '현재가';
}

/** 금액 입력칸에 보여줄 값: 숫자만 남기고 천 단위 구분 */
export function formatAmountInput(value: string): string {
  const amount = parseAmount(value);
  return amount === undefined ? '' : amount.toLocaleString('ko-KR');
}
