import { describe, expect, it } from 'vitest';
import {
  auctionPhase,
  auctionStatusTab,
  childCategoryOptions,
  durationLabel,
  EMPTY_AUCTION_CONDITION,
  LIVE_AUCTION_DURATIONS,
  featuredAuctions,
  formatAmountInput,
  parentCategoryOf,
  parentCategoryOptions,
  parseAmount,
  secondsUntil,
  toAuctionCreateRequest,
  toLiveAuctionCreateRequest,
  validateAuctionCondition, auctionPriceLabel } from './auction';

const NOW = new Date('2026-10-21T12:00:00');

const categories = [
  { id: 1, name: '카메라', children: [{ id: 11, name: '필름 카메라' }, { id: 12, name: '렌즈' }] },
  { id: 2, name: '시계', children: [] },
];

describe('featuredAuctions', () => {
  const auctions = [
    { id: 1, status: 'ONGOING' as const, endAt: '2026-10-21T12:05:00' },
    { id: 2, status: 'READY' as const, endAt: '2026-10-21T13:00:00' },
    { id: 3, status: 'ONGOING' as const, endAt: '2026-10-21T11:59:00' },
    { id: 4, status: 'ENDED' as const, endAt: '2026-10-21T11:00:00' },
    { id: 5, status: 'CANCELED' as const, endAt: '2026-10-21T13:00:00' },
  ];
  const ids = (list: { id: number }[]) => list.map((a) => a.id);

  it('띄운 경매가 시작 전·진행 중·마감 집계 중이면 띄운 순서대로', () => {
    expect(ids(featuredAuctions([2, 1], auctions, NOW))).toEqual([2, 1]);
    expect(ids(featuredAuctions([3], auctions, NOW))).toEqual([3]);
  });

  it('끝났거나 취소됐거나 목록에 없는 경매는 빼고, 띄운 경매가 없으면 빈 목록', () => {
    expect(ids(featuredAuctions([4, 1], auctions, NOW))).toEqual([1]);
    expect(featuredAuctions([5, 99], auctions, NOW)).toEqual([]);
    expect(featuredAuctions(undefined, auctions, NOW)).toEqual([]);
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
  it('대분류 선택지, 상품 등록은 소분류가 있는 대분류만', () => {
    expect(parentCategoryOptions(categories).map((o) => o.value)).toEqual(['1', '2']);
    expect(parentCategoryOptions(categories, true)).toEqual([{ value: '1', label: '카메라' }]);
  });

  it('고른 id가 속한 대분류와 그 소분류 선택지', () => {
    expect(parentCategoryOf(categories, '12')?.id).toBe(1);
    expect(parentCategoryOf(categories, '2')?.id).toBe(2);
    expect(parentCategoryOf(categories, '')).toBeUndefined();
    expect(parentCategoryOf(categories, '99')).toBeUndefined();
    expect(childCategoryOptions(categories[0])).toEqual([
      { value: '11', label: '필름 카메라' },
      { value: '12', label: '렌즈' },
    ]);
    expect(childCategoryOptions(undefined)).toEqual([]);
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

describe('auctionPriceLabel', () => {
  it('즉시구매 전용은 즉시구매가·판매가, 입찰 경매는 시작가·현재가·낙찰가', () => {
    expect(auctionPriceLabel('ONGOING', true)).toBe('즉시구매가');
    expect(auctionPriceLabel('ENDED', true)).toBe('판매가');
    expect(auctionPriceLabel('READY', false)).toBe('시작가');
    expect(auctionPriceLabel('ONGOING', false)).toBe('현재가');
    expect(auctionPriceLabel('ENDED', false)).toBe('낙찰가');
  });
});

describe('validateAuctionCondition', () => {
  const schedule = { ...EMPTY_AUCTION_CONDITION, startPrice: '10000', bidUnit: '1000', startAt: '2026-10-21T13:00', endAt: '2026-10-21T14:00' };

  it('올바르면 오류가 없다', () => {
    expect(validateAuctionCondition(schedule, 'schedule', NOW)).toEqual({});
    expect(validateAuctionCondition({ ...schedule, startAt: '', endAt: '' }, 'live', NOW)).toEqual({});
  });

  it('금액이 0이거나 즉시구매가가 시작가 이하면 오류', () => {
    const errors = validateAuctionCondition({ ...schedule, startPrice: '0', bidUnit: '0', buyNowPrice: '5000' }, 'schedule', NOW);
    expect(errors.startPrice).toBeDefined();
    expect(errors.bidUnit).toBeDefined();
    expect(validateAuctionCondition({ ...schedule, buyNowPrice: '10000' }, 'schedule', NOW).buyNowPrice).toBe('즉시구매가는 시작가보다 커야 해요.');
  });

  it('[즉시구매만 판매]를 켜면 시작가·입찰 단위는 보지 않고 즉시구매가가 필요하다', () => {
    expect(validateAuctionCondition({ ...schedule, buyNowOnly: true, startPrice: '', bidUnit: '', buyNowPrice: '30,000' }, 'schedule', NOW)).toEqual({});
    expect(validateAuctionCondition({ ...schedule, buyNowOnly: true, buyNowPrice: '' }, 'schedule', NOW).buyNowPrice).toBe(
      '즉시구매만 판매하려면 즉시구매가를 입력해 주세요.',
    );
    expect(validateAuctionCondition({ ...schedule, buyNowOnly: true, buyNowPrice: '0' }, 'schedule', NOW).buyNowPrice).toBe(
      '즉시구매가는 0원일 수 없어요.',
    );
  });

  it('버튼을 끈 상태에서 비우면 입력해 달라고, 0이면 0원일 수 없다고 알려준다', () => {
    const empty = validateAuctionCondition({ ...schedule, startPrice: '', bidUnit: '' }, 'schedule', NOW);
    expect(empty.startPrice).toBe('시작가를 입력해 주세요.');
    expect(empty.bidUnit).toBe('최소 입찰 단위를 입력해 주세요.');
    const zero = validateAuctionCondition({ ...schedule, startPrice: '0', bidUnit: '0' }, 'schedule', NOW);
    expect(zero.startPrice).toBe('시작가는 0원일 수 없어요.');
    expect(zero.bidUnit).toBe('최소 입찰 단위는 0원일 수 없어요.');
  });

  it('[즉시구매만 판매]면 시작가·입찰 단위를 요청에 넣지 않는다 (입력해 둔 값이 있어도)', () => {
    const request = toAuctionCreateRequest(1, { ...schedule, buyNowOnly: true, buyNowPrice: '30,000' });
    expect(request.startPrice).toBeUndefined();
    expect(request.bidUnit).toBeUndefined();
    expect(request.buyNowPrice).toBe(30000);
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
