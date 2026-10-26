import { Link } from 'react-router';
import type { Schema } from '@/api';
import { Notice } from '@/components/ui';
import { formatPoint } from '@/lib/format';

type MyBid = Schema<'MyBidResponse'>;

/**
 * 이 경매에서 내 입찰 상태 한 줄 안내. 입찰한 적 없으면(NONE) 아무것도 그리지 않는다.
 * - 최고 입찰 중 / 밀림(다시 입찰 가능) / 낙찰(주문 내역으로) / 낙찰받지 못함
 */
export function MyBidStatus({ myBid, onRebid }: { myBid?: MyBid; onRebid?: () => void }) {
  const amount = myBid?.myHighestAmount;
  switch (myBid?.state) {
    case 'LEADING':
      return (
        <Notice tone="success">
          지금 최고 입찰자예요.{amount ? ` 내 입찰 ${formatPoint(amount)}` : ''}
        </Notice>
      );
    case 'OUTBID':
      return (
        <Notice>
          {amount ? `내 입찰 ${formatPoint(amount)}보다 ` : ''}높은 입찰이 들어왔어요.{' '}
          {onRebid ? (
            <button type="button" onClick={onRebid} className="font-semibold underline">
              다시 입찰하기
            </button>
          ) : (
            '다시 입찰할 수 있어요.'
          )}
        </Notice>
      );
    case 'WON':
      return (
        <Notice tone="success">
          낙찰받았어요. 상품은{' '}
          <Link to="/orders" className="font-semibold underline">
            주문 내역
          </Link>
          에서 확인하세요.
        </Notice>
      );
    case 'LOST':
      return <Notice>이 경매는 낙찰받지 못했어요. 묶였던 포인트는 돌려받았어요.</Notice>;
    default:
      return null;
  }
}
