import type { ChangeEvent } from 'react';
import { Input, Select } from '@/components/ui';
import { durationLabel, formatAmountInput, LIVE_AUCTION_DURATIONS, type AuctionConditionErrors, type AuctionConditionValues } from '@/lib/auction';
import { dateTimeInputOf } from '@/lib/datetime-input';

type Props = {
  /** schedule: 시작·마감 시각을 정하는 경매 · live: 방송 중에 바로 시작하는 경매(진행 시간 선택) */
  mode: 'schedule' | 'live';
  values: AuctionConditionValues;
  errors: AuctionConditionErrors;
  onChange: (patch: Partial<AuctionConditionValues>) => void;
  /** 좁은 자리(판매자 스튜디오)에서는 두 칸씩 놓는다. */
  compact?: boolean;
};

const DURATION_OPTIONS = LIVE_AUCTION_DURATIONS.map((seconds) => ({ value: String(seconds), label: durationLabel(seconds) }));

/** 경매 조건 입력: 시작가·최소 입찰 단위·즉시구매가(선택) + 시작·마감 시각 또는 진행 시간. 상품 등록·경매 등록·스튜디오가 같이 쓴다. */
export function AuctionConditionFields({ mode, values, errors, onChange, compact = false }: Props) {
  const amount = (key: 'startPrice' | 'bidUnit' | 'buyNowPrice') => ({
    inputMode: 'numeric' as const,
    suffix: 'P',
    value: formatAmountInput(values[key]),
    onChange: (e: ChangeEvent<HTMLInputElement>) => onChange({ [key]: e.target.value }),
    error: errors[key],
  });
  const min = dateTimeInputOf(new Date());

  return (
    <div className={compact ? 'grid grid-cols-2 gap-3' : 'grid grid-cols-3 gap-4'}>
      <Input label="시작가" placeholder="10,000" {...amount('startPrice')} />
      <Input label="최소 입찰 단위" placeholder="1,000" {...amount('bidUnit')} />
      <Input label="즉시구매가 (선택)" placeholder="비워 두면 없음" hint={compact ? undefined : '누군가 즉시구매하면 경매가 바로 끝나요.'} {...amount('buyNowPrice')} />
      {mode === 'schedule' ? (
        <>
          <Input
            label="시작 시각"
            type="datetime-local"
            min={min}
            value={values.startAt}
            onChange={(e) => onChange({ startAt: e.target.value })}
            error={errors.startAt}
          />
          <Input
            label="마감 시각"
            type="datetime-local"
            min={values.startAt || min}
            value={values.endAt}
            onChange={(e) => onChange({ endAt: e.target.value })}
            error={errors.endAt}
          />
        </>
      ) : (
        <Select label="진행 시간" options={DURATION_OPTIONS} value={values.durationSec} onChange={(e) => onChange({ durationSec: e.target.value })} error={errors.durationSec} />
      )}
    </div>
  );
}
