import { Zap } from 'lucide-react';
import type { ChangeEvent } from 'react';
import { Input, Select } from '@/components/ui';
import { cn } from '@/lib/cn';
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

/**
 * 경매 조건 입력: 시작가·최소 입찰 단위·즉시구매가 + 시작·마감 시각 또는 진행 시간. 상품 등록·경매 등록·스튜디오가 같이 쓴다.
 * [즉시구매만 판매]를 켜면 시작가·최소 입찰 단위가 0으로 잠기고, 입찰 없이 즉시구매가로만 파는 경매가 된다. (이때 즉시구매가 필수)
 * 끄면 켜기 전에 입력한 값이 그대로 돌아온다. (켜 있는 동안 두 값을 지우지 않고 화면에만 0을 보여준다)
 */
export function AuctionConditionFields({ mode, values, errors, onChange, compact = false }: Props) {
  const amount = (key: 'startPrice' | 'bidUnit' | 'buyNowPrice') => {
    const locked = values.buyNowOnly && key !== 'buyNowPrice';
    return {
      inputMode: 'numeric' as const,
      suffix: 'P',
      value: locked ? '0' : formatAmountInput(values[key]),
      onChange: (e: ChangeEvent<HTMLInputElement>) => onChange({ [key]: e.target.value }),
      disabled: locked,
      error: locked ? undefined : errors[key],
    };
  };
  const min = dateTimeInputOf(new Date());

  return (
    <div className={compact ? 'grid grid-cols-2 gap-3' : 'grid grid-cols-3 gap-4'}>
      <div className="col-span-full flex flex-wrap items-center gap-3">
        <button
          type="button"
          aria-pressed={values.buyNowOnly}
          onClick={() => onChange({ buyNowOnly: !values.buyNowOnly })}
          className={cn(
            'inline-flex h-9 items-center gap-1.5 rounded-full border px-4 text-label font-semibold transition-colors',
            values.buyNowOnly ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-neutral-200 text-neutral-700 hover:bg-neutral-50',
          )}
        >
          <Zap className="size-4" aria-hidden="true" />
          즉시구매만 판매
        </button>
        <span className="text-caption text-neutral-500">
          {values.buyNowOnly ? '입찰 없이 즉시구매가로만 팔아요. 즉시구매가를 꼭 입력해 주세요.' : '켜면 입찰 없이 즉시구매가로만 팔아요.'}
        </span>
      </div>
      <Input label="시작가" placeholder="10,000" {...amount('startPrice')} />
      <Input label="최소 입찰 단위" placeholder="1,000" {...amount('bidUnit')} />
      <Input label="즉시구매가" placeholder="비워 두면 없음" hint={compact ? undefined : '누군가 즉시구매하면 경매가 바로 끝나요.'} {...amount('buyNowPrice')} />
      <p className="col-span-full -mt-1 text-caption text-neutral-500">
        판매되면 판매 수수료를 뺀 금액이 정산돼요.
      </p>
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
