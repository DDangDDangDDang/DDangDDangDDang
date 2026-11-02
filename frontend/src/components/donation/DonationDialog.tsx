import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { api, call, errorMessage } from '@/api';
import { Button, Dialog, Input, Notice, Skeleton, Textarea } from '@/components/ui';
import { formatAmountInput, parseAmount } from '@/lib/auction';
import { cn } from '@/lib/cn';
import { DONATION_HISTORY_KEY, DONATION_WALLET_KEY, donationStatusKey, giftPresets, newRequestKey, validateGift } from '@/lib/donation';
import { formatNumber, formatMileage } from '@/lib/format';

const MESSAGE_MAX_LENGTH = 20;

/**
 * 도네이션 선물 창: 방송 중인 판매자에게 도네이션 마일리지를 선물한다. (시청 화면)
 * 선물하면 방송 화면에 "누구님이 몇 M을 선물했어요" 이펙트가 뜬다. (채팅 DONATION 메시지)
 */
export function DonationDialog({ liveStreamId, sellerName, open, onClose }: { liveStreamId: number; sellerName: string; open: boolean; onClose: () => void }) {
  return (
    <Dialog open={open} title={`${sellerName}님에게 선물하기`} description="도네이션 마일리지로 방송을 응원해요. 선물은 취소할 수 없어요." onClose={onClose}>
      {open && <DonationForm liveStreamId={liveStreamId} />}
    </Dialog>
  );
}

function DonationForm({ liveStreamId }: { liveStreamId: number }) {
  const queryClient = useQueryClient();
  const status = useQuery({
    queryKey: donationStatusKey(liveStreamId),
    queryFn: () => call(api.GET('/api/v1/live-streams/{liveStreamId}/donations/me', { params: { path: { liveStreamId } } })),
  });
  const [amount, setAmount] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState<string>();
  // 선물 하나마다 새 요청 키. 실패해서 다시 보낼 때는 같은 키를 써서 두 번 선물되지 않게 한다.
  const [requestKey, setRequestKey] = useState(newRequestKey);
  const [sent, setSent] = useState<number>();

  const gift = useMutation({
    mutationFn: (value: number) =>
      call(
        api.POST('/api/v1/live-streams/{liveStreamId}/donations', {
          params: { path: { liveStreamId } },
          body: { amount: value, message: message.trim() || undefined, requestKey },
        }),
      ),
    onSuccess: (data, value) => {
      setSent(data?.amount ?? value);
      setAmount('');
      setMessage('');
      setRequestKey(newRequestKey());
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: donationStatusKey(liveStreamId) });
      void queryClient.invalidateQueries({ queryKey: DONATION_WALLET_KEY });
      void queryClient.invalidateQueries({ queryKey: DONATION_HISTORY_KEY });
    },
  });

  if (status.isPending) {
    return (
      <div className="flex flex-col gap-3">
        <Skeleton className="h-20" />
        <Skeleton className="h-11" />
      </div>
    );
  }
  if (status.isError || !status.data) {
    return (
      <Notice tone="error">
        선물 정보를 불러오지 못했어요. {errorMessage(status.error)}{' '}
        <button type="button" onClick={() => void status.refetch()} className="font-semibold underline">
          다시 불러오기
        </button>
      </Notice>
    );
  }

  const info = status.data;
  const value = parseAmount(amount);
  const presets = giftPresets(info);

  const changeAmount = (next: string) => {
    setAmount(next);
    setError(undefined);
    setSent(undefined);
    gift.reset();
    // 다른 금액이면 다른 선물이다.
    setRequestKey(newRequestKey());
  };

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    const reason = validateGift(value, info);
    setError(reason);
    if (reason || value === undefined) return;
    gift.mutate(value);
  };

  return (
    <form onSubmit={onSubmit} noValidate aria-label="선물" className="flex flex-col gap-4">
      <dl className="grid grid-cols-2 gap-2 rounded-md bg-neutral-25 px-4 py-3">
        <div className="flex flex-col">
          <dt className="text-caption text-neutral-500">내 마일리지</dt>
          <dd className="num text-title-md font-bold">{formatMileage(info.balance ?? 0)}</dd>
        </div>
        <div className="flex flex-col">
          <dt className="text-caption text-neutral-500">이 판매자에게 이번 달 남은 한도</dt>
          <dd className="num text-title-md font-bold">{formatMileage(info.remainingThisMonth ?? 0)}</dd>
        </div>
      </dl>

      {(info.balance ?? 0) < (info.minGift ?? 0) ? (
        <Notice>
          아직 선물할 도네이션 마일리지가 없어요. 구매·판매를 확정하면 낙찰가의 일부가 도네이션 마일리지로 쌓여요.{' '}
          <Link to="/me/donation-points" target="_blank" rel="noreferrer" className="font-semibold underline">
            도네이션 마일리지 보기
          </Link>
        </Notice>
      ) : (
        <>
          <div role="group" aria-label="금액 빠르게 정하기" className="grid grid-cols-5 gap-2">
            {presets.map((preset) => (
              <button
                key={preset}
                type="button"
                aria-pressed={value === preset}
                onClick={() => changeAmount(String(preset))}
                className={cn(
                  'num h-10 rounded-md border text-label font-semibold transition-colors',
                  value === preset ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-neutral-200 hover:bg-neutral-25',
                )}
              >
                {formatNumber(preset)}
              </button>
            ))}
          </div>
          <Input
            label="선물할 마일리지"
            inputMode="numeric"
            suffix="M"
            placeholder={formatNumber(info.minGift ?? 0)}
            value={formatAmountInput(amount)}
            error={error}
            hint={`${formatMileage(info.minGift ?? 0)}부터 ${formatMileage(info.maxGift ?? 0)}까지 ${formatMileage(info.giftUnit ?? 0)} 단위로 선물할 수 있어요.`}
            onChange={(e) => changeAmount(e.target.value)}
          />
          <Textarea
            label="함께 보낼 한마디 (선택)"
            rows={2}
            maxLength={MESSAGE_MAX_LENGTH}
            value={message}
            hint={`방송 화면에 함께 보여요. 연락처·외부 링크는 쓸 수 없어요. ${message.length}/${MESSAGE_MAX_LENGTH}`}
            onChange={(e) => {
              setMessage(e.target.value);
              gift.reset();
            }}
          />
          {gift.isError && <Notice tone="error">{errorMessage(gift.error)}</Notice>}
          {sent !== undefined && <Notice tone="success">{formatMileage(sent)}을 선물했어요. 방송 화면에 선물 알림이 떠요.</Notice>}
          <Button type="submit" size="lg" loading={gift.isPending}>
            {value ? `${formatMileage(value)} 선물하기` : '선물하기'}
          </Button>
        </>
      )}
    </form>
  );
}
