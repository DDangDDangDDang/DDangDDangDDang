import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api, call, errorMessage, useSession, type Schema } from '@/api';
import { PageHeader } from '@/components/layout/PageHeader';
import { Badge, Button, Card, CardBody, CardHeader, CardTitle, EmptyState, ErrorState, Input, Notice, Skeleton } from '@/components/ui';
import { formatDateTime, formatNumber, onlyDigits } from '@/lib/format';

type Policy = Schema<'DonationPolicy'>;
type PolicyKey = keyof Policy;

const POLICY_KEY = ['admin', 'donation-policy'] as const;
const HISTORY_KEY = ['admin', 'donation-policy', 'history'] as const;

/** 정책 항목: 화면 문구와 단위. 순서대로 묶음별로 보여 준다. */
const GROUPS: { title: string; fields: { key: PolicyKey; label: string; unit: string; hint?: string }[] }[] = [
  {
    title: '적립 (구매 확정)',
    fields: [
      { key: 'earnRatePercent', label: '적립 비율', unit: '%', hint: '낙찰가의 몇 %를 적립할지' },
      { key: 'maxEarnPerOrder', label: '주문당 최대 적립', unit: 'M' },
      { key: 'monthlyEarnLimit', label: '한 달 적립 한도', unit: 'M', hint: '1일~말일' },
      { key: 'pointValidDays', label: '유효기간', unit: '일', hint: '적립한 날부터' },
      { key: 'expiringNoticeDays', label: '만료 알림', unit: '일 전', hint: '유효기간보다 짧게' },
    ],
  },
  {
    title: '선물',
    fields: [
      { key: 'minGift', label: '최소 선물', unit: 'M' },
      { key: 'giftUnit', label: '선물 단위', unit: 'M' },
      { key: 'maxGift', label: '최대 선물 (한 번)', unit: 'M' },
      { key: 'monthlyGiftLimitPerSeller', label: '판매자 한 명에게 한 달 한도', unit: 'M', hint: '계정 하나 기준' },
      { key: 'trustWindowDays', label: '응원 지표 기간', unit: '일', hint: '판매자 프로필의 응원한 사람 수' },
    ],
  },
  {
    title: '판매자 상점',
    fields: [
      { key: 'powerSellerPrice', label: '파워 판매자 가격', unit: 'M' },
      { key: 'powerSellerDays', label: '파워 판매자 기간', unit: '일' },
      { key: 'promotedLiveSlots', label: '라이브 목록 맨 위 칸 수', unit: '개' },
    ],
  },
];

/**
 * 관리자: 도네이션 마일리지 정책. 바꾸면 배포·재시작 없이 바로 모든 서버에 적용되고, 바꾼 기록이 남는다.
 * 이미 적립·선물한 마일리지는 바뀌지 않는다.
 */
export function DonationPolicyPage() {
  const session = useSession();
  const isAdmin = session?.user.role === 'ADMIN';
  const current = useQuery({
    queryKey: POLICY_KEY,
    queryFn: () => call(api.GET('/api/v1/admin/donation-policy')),
    enabled: isAdmin,
  });

  if (!isAdmin) {
    return <EmptyState title="관리자만 쓸 수 있는 화면이에요" description="도네이션 마일리지 정책은 관리자가 정해요." />;
  }

  return (
    <div className="flex flex-col gap-4">
      <PageHeader title="도네이션 정책" description="적립·선물·판매자 상점 규칙이에요. 바꾸면 바로 모든 서버에 적용되고, 이미 쌓인 마일리지는 그대로예요." />
      {current.isPending ? (
        <Skeleton className="h-96" />
      ) : current.isError || !current.data?.policy ? (
        <ErrorState message={errorMessage(current.error)} onRetry={() => void current.refetch()} />
      ) : (
        <PolicyForm key={current.data.id ?? 'defaults'} policy={current.data.policy} usingDefaults={current.data.usingDefaults ?? false} />
      )}
      <History />
    </div>
  );
}

function PolicyForm({ policy, usingDefaults }: { policy: Policy; usingDefaults: boolean }) {
  const queryClient = useQueryClient();
  const [values, setValues] = useState<Record<PolicyKey, string>>(
    () => Object.fromEntries(Object.entries(policy).map(([key, value]) => [key, String(value ?? '')])) as Record<PolicyKey, string>,
  );
  const [memo, setMemo] = useState('');
  const [saved, setSaved] = useState(false);

  const save = useMutation({
    mutationFn: () => {
      const body = Object.fromEntries(Object.entries(values).map(([key, value]) => [key, Number(value)])) as Required<Policy>;
      return call(api.PUT('/api/v1/admin/donation-policy', { body: { ...body, memo: memo.trim() || undefined } }));
    },
    onSuccess: () => {
      setSaved(true);
      setMemo('');
      void queryClient.invalidateQueries({ queryKey: POLICY_KEY });
      void queryClient.invalidateQueries({ queryKey: HISTORY_KEY });
    },
  });

  const empty = Object.values(values).some((value) => value === '');
  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!empty) save.mutate();
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>지금 정책</CardTitle>
        {usingDefaults && <Badge variant="outline">처음 설정값 사용 중</Badge>}
      </CardHeader>
      <CardBody>
        <form onSubmit={onSubmit} noValidate aria-label="도네이션 정책" className="flex flex-col gap-5">
          {GROUPS.map((group) => (
            <fieldset key={group.title} className="flex flex-col gap-3">
              <legend className="mb-2 text-body font-bold">{group.title}</legend>
              <div className="grid grid-cols-3 gap-4">
                {group.fields.map((field) => (
                  <Input
                    key={field.key}
                    label={field.label}
                    inputMode="numeric"
                    suffix={field.unit}
                    hint={field.hint}
                    value={values[field.key]}
                    error={values[field.key] === '' ? '값을 입력해 주세요.' : undefined}
                    onChange={(e) => {
                      setSaved(false);
                      save.reset();
                      setValues((prev) => ({ ...prev, [field.key]: onlyDigits(e.target.value) }));
                    }}
                  />
                ))}
              </div>
            </fieldset>
          ))}
          <Input label="바꾼 이유 (선택)" maxLength={200} placeholder="예) 11월 적립 2% 이벤트" value={memo} onChange={(e) => setMemo(e.target.value)} />
          {save.isError && <Notice tone="error">{errorMessage(save.error)}</Notice>}
          {saved && <Notice tone="success">정책을 바꿨어요. 지금부터 모든 적립·선물에 적용돼요.</Notice>}
          <div className="flex justify-end">
            <Button type="submit" loading={save.isPending} disabled={empty}>
              정책 저장
            </Button>
          </div>
        </form>
      </CardBody>
    </Card>
  );
}

function History() {
  const history = useQuery({
    queryKey: HISTORY_KEY,
    queryFn: () => call(api.GET('/api/v1/admin/donation-policy/history', { params: { query: { size: 20 } } })),
  });
  const rows = history.data?.content ?? [];

  return (
    <Card>
      <CardHeader>
        <CardTitle>바꾼 기록</CardTitle>
      </CardHeader>
      <CardBody className="px-0 pb-0">
        {history.isPending ? (
          <Skeleton className="mx-5 mb-5 h-24" />
        ) : history.isError ? (
          <ErrorState message={errorMessage(history.error)} onRetry={() => void history.refetch()} />
        ) : rows.length === 0 ? (
          <EmptyState title="아직 바꾼 적이 없어요" description="정책을 저장하면 언제 어떤 값으로 바꿨는지 여기에 남아요." />
        ) : (
          <table className="w-full text-body">
            <thead className="border-y border-neutral-100 bg-neutral-25 text-label text-neutral-500">
              <tr>
                <th scope="col" className="px-5 py-2.5 text-left font-semibold">
                  바꾼 때
                </th>
                <th scope="col" className="px-5 py-2.5 text-left font-semibold">
                  이유
                </th>
                <th scope="col" className="px-5 py-2.5 text-right font-semibold">
                  적립
                </th>
                <th scope="col" className="px-5 py-2.5 text-right font-semibold">
                  판매자당 한 달 선물
                </th>
                <th scope="col" className="px-5 py-2.5 text-right font-semibold">
                  유효기간
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id} className="border-b border-neutral-100 last:border-b-0">
                  <td className="px-5 py-3 text-neutral-500">{row.changedAt ? formatDateTime(row.changedAt) : '-'}</td>
                  <td className="px-5 py-3">{row.memo || '-'}</td>
                  <td className="num px-5 py-3 text-right">
                    {row.policy?.earnRatePercent ?? 0}% · 월 {formatNumber(row.policy?.monthlyEarnLimit ?? 0)}P
                  </td>
                  <td className="num px-5 py-3 text-right">{formatNumber(row.policy?.monthlyGiftLimitPerSeller ?? 0)}M</td>
                  <td className="num px-5 py-3 text-right">{formatNumber(row.policy?.pointValidDays ?? 0)}일</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </CardBody>
    </Card>
  );
}
