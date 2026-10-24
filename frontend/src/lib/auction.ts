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

/** 상품을 등록할 수 있는 카테고리(소분류)만 "대분류 › 소분류"로. 서버는 소분류만 받는다. (PRODUCT_003) */
export function leafCategoryOptions(categories: Category[]): { value: string; label: string }[] {
  return categories.flatMap((parent) =>
    (parent.children ?? []).filter((child) => child.id !== undefined).map((child) => ({ value: String(child.id), label: `${parent.name ?? ''} › ${child.name ?? ''}` })),
  );
}

/** 검색 필터용: 대분류와 소분류 모두 (소분류는 들여쓰기) */
export function categoryFilterOptions(categories: Category[]): { value: string; label: string }[] {
  return categories.flatMap((parent) => [
    { value: String(parent.id), label: parent.name ?? '' },
    ...(parent.children ?? []).map((child) => ({ value: String(child.id), label: `└ ${child.name ?? ''}` })),
  ]);
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

/** 경매 조건 입력. 금액은 입력 문자열, 시각은 datetime-local 값 */
export type AuctionConditionValues = {
  startPrice: string;
  bidUnit: string;
  buyNowPrice: string;
  startAt: string;
  endAt: string;
  durationSec: string;
};
export type AuctionConditionErrors = Partial<Record<keyof AuctionConditionValues, string>>;

export const EMPTY_AUCTION_CONDITION: AuctionConditionValues = { startPrice: '', bidUnit: '', buyNowPrice: '', startAt: '', endAt: '', durationSec: '60' };

/**
 * 경매 조건을 검사한다. schedule은 시작·마감 시각을 정하는 경매, live는 방송 중에 바로 시작하는 경매.
 * (서버도 같은 규칙으로 다시 검사한다: 금액 양수, 즉시구매가 > 시작가, 시각은 미래·마감 > 시작)
 */
export function validateAuctionCondition(values: AuctionConditionValues, mode: 'schedule' | 'live', now: Date): AuctionConditionErrors {
  const errors: AuctionConditionErrors = {};
  const startPrice = parseAmount(values.startPrice);
  const bidUnit = parseAmount(values.bidUnit);
  const buyNowPrice = parseAmount(values.buyNowPrice);

  if (!startPrice) errors.startPrice = '시작가를 입력해 주세요.';
  if (!bidUnit) errors.bidUnit = '최소 입찰 단위를 입력해 주세요.';
  if (buyNowPrice !== undefined && startPrice && buyNowPrice <= startPrice) errors.buyNowPrice = '즉시구매가는 시작가보다 커야 해요.';

  if (mode === 'schedule') {
    const startAt = values.startAt ? new Date(values.startAt) : undefined;
    const endAt = values.endAt ? new Date(values.endAt) : undefined;
    if (!startAt) errors.startAt = '시작 시각을 정해 주세요.';
    else if (startAt <= now) errors.startAt = '시작 시각은 지금 이후로 정해 주세요.';
    if (!endAt) errors.endAt = '마감 시각을 정해 주세요.';
    else if (startAt && endAt <= startAt) errors.endAt = '마감 시각은 시작 시각보다 뒤여야 해요.';
  } else if (!LIVE_AUCTION_DURATIONS.includes(Number(values.durationSec) as (typeof LIVE_AUCTION_DURATIONS)[number])) {
    errors.durationSec = '진행 시간을 선택해 주세요.';
  }
  return errors;
}

export function toAuctionCreateRequest(productId: number, values: AuctionConditionValues): Schema<'AuctionCreateRequest'> {
  return {
    productId,
    startPrice: parseAmount(values.startPrice) ?? 0,
    bidUnit: parseAmount(values.bidUnit) ?? 0,
    buyNowPrice: parseAmount(values.buyNowPrice),
    startAt: fromDateTimeInput(values.startAt) ?? '',
    endAt: fromDateTimeInput(values.endAt) ?? '',
  };
}

export function toLiveAuctionCreateRequest(productId: number, values: AuctionConditionValues): Schema<'LiveAuctionCreateRequest'> {
  return {
    productId,
    startPrice: parseAmount(values.startPrice) ?? 0,
    bidUnit: parseAmount(values.bidUnit) ?? 0,
    buyNowPrice: parseAmount(values.buyNowPrice),
    durationSec: Number(values.durationSec),
  };
}

/** 금액 입력칸에 보여줄 값: 숫자만 남기고 천 단위 구분 */
export function formatAmountInput(value: string): string {
  const amount = parseAmount(value);
  return amount === undefined ? '' : amount.toLocaleString('ko-KR');
}
