import { useQuery } from '@tanstack/react-query';
import { api, call } from '@/api';
import { Notice } from '@/components/ui';
import { formatDateTime } from '@/lib/format';
import { SANCTION_CAUSE_LABEL, type SanctionType } from '@/lib/labels';

const SANCTION_TITLE: Record<SanctionType, string> = {
  BID_BAN: '입찰·즉시 구매가 정지됐어요',
  LISTING_BAN: '경매 등록이 정지됐어요',
};

/** 내 정지 안내. 본인 책임 취소가 반복돼 정지 중일 때만 보인다. */
export function MySanctionsNotice({ className }: { className?: string }) {
  const query = useQuery({
    queryKey: ['me', 'sanctions'],
    queryFn: () => call(api.GET('/api/v1/users/me/sanctions')),
  });
  const sanctions = query.data ?? [];
  if (sanctions.length === 0) return null;

  return (
    <div className={className}>
      {sanctions.map((sanction) =>
        sanction.type ? (
          <Notice key={sanction.type} tone="error" className="mb-2">
            <strong>{SANCTION_TITLE[sanction.type]}</strong> 최근 90일 {SANCTION_CAUSE_LABEL[sanction.type]} {sanction.cancelCount}회
            {sanction.bannedUntil && ` · ${formatDateTime(sanction.bannedUntil)}에 풀려요`}
          </Notice>
        ) : null,
      )}
    </div>
  );
}
