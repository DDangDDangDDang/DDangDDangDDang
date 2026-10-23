import { describe, expect, it } from 'vitest';
import {
  auctionPhase,
  auctionStatusTab,
  categoryFilterOptions,
  durationLabel,
  EMPTY_AUCTION_CONDITION,
  LIVE_AUCTION_DURATIONS,
  featuredAuction,
  formatAmountInput,
  leafCategoryOptions,
  parseAmount,
  secondsUntil,
  toAuctionCreateRequest,
  toLiveAuctionCreateRequest,
  validateAuctionCondition,
} from './auction';

const NOW = new Date('2026-10-21T12:00:00');

const categories = [
  { id: 1, name: '카메라', children: [{ id: 11, name: '필름 카메라' }, { id: 12, name: '렌즈' }] },
  { id: 2, name: '시계', children: [] },
];

describe('featuredAuction', () => {
  const auctions = [
    { id: 1, status: 'ONGOING' as const, endAt: '2026-10-21T12:05:00' },
    { id: 2, status: 'READY' as const, endAt: '2026-10-21T13:00:00' },
    { id: 3, status: 'ONGOING' as const, endAt: '2026-10-21T11:59:00' },
    { id: 4, status: 'ENDED' as const, endAt: '2026-10-21T11:00:00' },
    { id: 5, status: 'CANCELED' as const, endAt: '2026-10-21T13:00:00' },
  ];

  it('띄운 경매가 시작 전·진행 중·마감 집계 중이면 그 경매', () => {
    expect(featuredAuction(1, auctions, NOW)?.id).toBe(1);
    expect(featuredAuction(2, auctions, NOW)?.id).toBe(2);
    expect(featuredAuction(3, auctions, NOW)?.id).toBe(3);
  });

  it('끝났거나 취소됐거나 목록에 없거나 띄운 경매가 없으면 없음', () => {
    expect(featuredAuction(4, auctions, NOW)).toBeUndefined();
    expect(featuredAuction(5, auctions, NOW)).toBeUndefined();
    expect(featuredAuction(99, auctions, NOW)).toBeUndefined();
    expect(featuredAuction(undefined, auctions, NOW)).toBeUndefined();
  });
});

describe('auctionPhase', () => {
  it('진행 중인데 마감 시각이 지났으면 마감으로 본다', () => {
    expect(auctionPhase({ status: 'ONGOING', endAt: '2026-10-21T11:59:59' }, NOW)).toBe('CLOSING');
    expect(auctionPhase({ status: 'ONGOING', endAt: '2026-10-21T12:00:30' }, NOW)).toBe('ONGOING');
  });

  it('그 밖의 상태는 그대로', () => {
    expect(auctionPhase({ status: 'ENDED', endAt: '2026-10-21T11:00:00' }, NOW)).toBe('ENDED');
    expect(auctionPhase({}, NOW)).toBeUndefined();
  });
});

describe('secondsUntil', () => {
  it('남은 초, 지났으면 0', () => {
    expect(secondsUntil('2026-10-21T12:01:30', NOW)).toBe(90);
    expect(secondsUntil('2026-10-21T11:00:00', NOW)).toBe(0);
    expect(secondsUntil(undefined, NOW)).toBe(0);
  });
});

describe('카테고리 선택지', () => {
  it('상품 등록은 소분류만 고른다', () => {
    expect(leafCategoryOptions(categories)).toEqual([
      { value: '11', label: '카메라 › 필름 카메라' },
      { value: '12', label: '카메라 › 렌즈' },
    ]);
  });

  it('검색 필터는 대분류와 소분류 모두', () => {
    expect(categoryFilterOptions(categories).map((o) => o.value)).toEqual(['1', '11', '12', '2']);
  });
});

describe('금액 입력', () => {
  it('숫자만 꺼내고 천 단위로 보여준다', () => {
    expect(parseAmount('12,000 P')).toBe(12000);
    expect(parseAmount('')).toBeUndefined();
    expect(formatAmountInput('1500000')).toBe('1,500,000');
    expect(formatAmountInput('abc')).toBe('');
  });
});

describe('validateAuctionCondition', () => {
  const schedule = { ...EMPTY_AUCTION_CONDITION, startPrice: '10000', bidUnit: '1000', startAt: '2026-10-21T13:00', endAt: '2026-10-21T14:00' };

  it('올바르면 오류가 없다', () => {
    expect(validateAuctionCondition(schedule, 'schedule', NOW)).toEqual({});
    expect(validateAuctionCondition({ ...schedule, startAt: '', endAt: '' }, 'live', NOW)).toEqual({});
  });

  it('금액이 없거나 즉시구매가가 시작가 이하면 오류', () => {
    const errors = validateAuctionCondition({ ...schedule, startPrice: '', bidUnit: '0', buyNowPrice: '5000' }, 'schedule', NOW);
    expect(errors.startPrice).toBeDefined();
    expect(errors.bidUnit).toBeDefined();
    expect(validateAuctionCondition({ ...schedule, buyNowPrice: '10000' }, 'schedule', NOW).buyNowPrice).toBe('즉시구매가는 시작가보다 커야 해요.');
  });

  it('시작은 지금 이후, 마감은 시작 뒤', () => {
    expect(validateAuctionCondition({ ...schedule, startAt: '2026-10-21T11:00' }, 'schedule', NOW).startAt).toBeDefined();
    expect(validateAuctionCondition({ ...schedule, endAt: '2026-10-21T13:00' }, 'schedule', NOW).endAt).toBe('마감 시각은 시작 시각보다 뒤여야 해요.');
  });

  it('방송 중 경매는 정해진 진행 시간만', () => {
    expect(validateAuctionCondition({ ...schedule, durationSec: '45' }, 'live', NOW).durationSec).toBeDefined();
  });
});

describe('요청 본문', () => {
  const values = { ...EMPTY_AUCTION_CONDITION, startPrice: '10,000', bidUnit: '1,000', startAt: '2026-10-21T13:00', endAt: '2026-10-21T14:00', durationSec: '120' };

  it('예약 경매', () => {
    expect(toAuctionCreateRequest(7, values)).toEqual({
      productId: 7,
      startPrice: 10000,
      bidUnit: 1000,
      buyNowPrice: undefined,
      startAt: '2026-10-21T13:00:00',
      endAt: '2026-10-21T14:00:00',
    });
  });

  it('방송 중 경매', () => {
    expect(toLiveAuctionCreateRequest(7, { ...values, buyNowPrice: '50000' })).toEqual({ productId: 7, startPrice: 10000, bidUnit: 1000, buyNowPrice: 50000, durationSec: 120 });
  });
});

describe('durationLabel', () => {
  it('초·분·시간·일로 보여준다', () => {
    expect(durationLabel(30)).toBe('30초');
    expect(durationLabel(300)).toBe('5분');
    expect(durationLabel(3 * 3600)).toBe('3시간');
    expect(durationLabel(3 * 86400)).toBe('3일');
  });

  it('진행 시간은 30초부터 3일까지 고를 수 있다 (서버 한도와 같음)', () => {
    expect(LIVE_AUCTION_DURATIONS[0]).toBe(30);
    expect(LIVE_AUCTION_DURATIONS.at(-1)).toBe(3 * 86400);
  });
});

describe('auctionStatusTab', () => {
  it('진행 중·시작 전은 그 탭, 마감·낙찰·유찰·취소는 종료 탭', () => {
    expect(auctionStatusTab('ONGOING')).toBe('ONGOING');
    expect(auctionStatusTab('READY')).toBe('READY');
    for (const phase of ['CLOSING', 'ENDED', 'FAILED', 'CANCELED', undefined] as const) {
      expect(auctionStatusTab(phase)).toBe('ENDED');
    }
  });
});
