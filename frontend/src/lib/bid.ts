import { parseAmount } from './auction';

type BidAuction = { startPrice?: number; currentPrice?: number; bidUnit?: number; bidCount?: number };

/** 지금 입찰할 수 있는 최소 금액. 첫 입찰은 시작가부터, 그다음은 현재가 + 최소 입찰 단위부터 */
export function minimumBid(auction: BidAuction): number {
  const start = auction.startPrice ?? 0;
  if ((auction.bidCount ?? 0) === 0) return start;
  return (auction.currentPrice ?? start) + (auction.bidUnit ?? 0);
}

/** 입찰 금액 입력을 확인한다. 문제가 없으면 undefined */
export function validateBid(value: string, auction: BidAuction): string | undefined {
  const amount = parseAmount(value);
  if (!amount) return '입찰할 금액을 입력해 주세요.';
  const min = minimumBid(auction);
  if (amount < min) return `${min.toLocaleString('ko-KR')} P부터 입찰할 수 있어요.`;
  return undefined;
}

/** 금액 빠르게 올리기: 최소 입찰가에서 입찰 단위의 몇 배를 더할지 */
export const BID_STEPS = [0, 1, 5, 10] as const;
