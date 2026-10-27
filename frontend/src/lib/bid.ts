import { parseAmount } from './auction';

type BidAuction = { startPrice?: number; currentPrice?: number; bidUnit?: number; bidCount?: number; buyNowPrice?: number };

/** 지금 입찰할 수 있는 최소 금액. 첫 입찰은 시작가부터, 그다음은 현재가 + 최소 입찰 단위부터 */
export function minimumBid(auction: BidAuction): number {
  const start = auction.startPrice ?? 0;
  if ((auction.bidCount ?? 0) === 0) return start;
  return (auction.currentPrice ?? start) + (auction.bidUnit ?? 0);
}

/**
 * 입찰 금액의 기준가. 첫 입찰은 시작가, 그다음은 현재가. 입찰은 기준가에서 입찰 단위만큼씩 올릴 수 있다. (서버 규칙과 같다)
 */
export function bidBase(auction: BidAuction): number {
  const start = auction.startPrice ?? 0;
  if ((auction.bidCount ?? 0) === 0) return start;
  return auction.currentPrice ?? start;
}

/** 입찰 금액 입력을 확인한다. 문제가 없으면 undefined */
export function validateBid(value: string, auction: BidAuction): string | undefined {
  const amount = parseAmount(value);
  if (!amount) return '입찰할 금액을 입력해 주세요.';
  const min = minimumBid(auction);
  if (amount < min) return `${min.toLocaleString('ko-KR')} P부터 입찰할 수 있어요.`;
  if (auction.buyNowPrice && amount >= auction.buyNowPrice) return '즉시구매가 이상으로는 입찰할 수 없어요. [즉시 구매]를 이용해 주세요.';
  const unit = auction.bidUnit ?? 0;
  if (unit > 0 && (amount - bidBase(auction)) % unit !== 0) return `${unit.toLocaleString('ko-KR')} P 단위로 입찰할 수 있어요.`;
  return undefined;
}

/** 금액 빠르게 올리기: 기준가(현재가, 입찰이 없으면 시작가)에 더할 금액 */
export const BID_SHORTCUTS = [5_000, 10_000, 30_000] as const;

/**
 * 금액 빠르게 정하기 버튼. [최소]는 최소 입찰가, [+N]은 기준가 + N이다.
 * 예) 현재가 20,000P·단위 1,000P → 최소 21,000 / +5,000 → 25,000 / +10,000 → 30,000 / +30,000 → 50,000
 * ([+N]에 입찰 단위를 한 번 더 더하지 않는다. 버튼에 적힌 금액만큼만 오른다)
 * 입찰 단위로 나눠떨어지지 않거나(단위 3,000P면 +5,000) 최소 입찰가보다 낮은(단위 10,000P면 +5,000) 버튼은 빼서,
 * 누르면 항상 입찰할 수 있는 금액만 들어가게 한다.
 */
export function bidPresets(auction: BidAuction): { label: string; amount: number }[] {
  const unit = auction.bidUnit ?? 0;
  const base = bidBase(auction);
  const min = minimumBid(auction);
  const shortcuts = BID_SHORTCUTS.filter((step) => unit > 0 && step % unit === 0 && base + step >= min).map((step) => ({
    label: `+${step.toLocaleString('ko-KR')}`,
    amount: base + step,
  }));
  return [{ label: '최소', amount: min }, ...shortcuts];
}
