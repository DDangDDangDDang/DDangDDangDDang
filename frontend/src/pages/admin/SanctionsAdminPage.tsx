import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, call, errorMessage, useSession, type Schema } from '@/api';
import { PageHeader } from '@/components/layout/PageHeader';
import { Badge, Button, Card, ConfirmDialog, EmptyState, ErrorState, Notice, Skeleton } from '@/components/ui';
import { formatDateTime } from '@/lib/format';
import { SANCTION_CAUSE_LABEL, SANCTION_TYPE_LABEL } from '@/lib/labels';

type Target = Schema<'AdminSanctionResponse'>;
/** 정지를 풀 수 있는 대상: 회원 ID와 정지 종류가 모두 있는 항목 */
type Releasable = Target & { userId: number; type: NonNullable<Target['type']> };

function isReleasable(target: Target): target is Releasable {
  return target.userId != null && target.type != null;
}

/**
 * 정지 검토 (관리자): 최근 90일 본인 책임 취소가 4회 이상인 회원.
 * 정지를 풀면 그때까지의 취소는 다시 세지 않는다.
 */
export function SanctionsAdminPage() {
  const session = useSession();
  const queryClient = useQueryClient();
  const [releasing, setReleasing] = useState<Releasable>();

  const query = useQuery({
    queryKey: ['admin', 'sanctions'],
    queryFn: () => call(api.GET('/api/v1/admin/sanctions')),
    enabled: session?.user.role === 'ADMIN',
  });
  const release = useMutation({
    mutationFn: ({ userId, type }: Releasable) =>
      call(api.POST('/api/v1/admin/sanctions/release', { body: { userId, type } })),
    onSuccess: () => setReleasing(undefined),
    onSettled: () => void queryClient.invalidateQueries({ queryKey: ['admin', 'sanctions'] }),
  });
  const targets = query.data ?? [];

  if (session?.user.role !== 'ADMIN') {
    return <EmptyState title="관리자만 쓸 수 있는 화면이에요" description="정지 검토는 관리자가 해요." />;
  }

  return (
    <div className="mx-auto max-w-[960px]">
      <PageHeader title="정지 검토" description="최근 90일 동안 본인 책임 취소가 4회 이상인 회원이에요. 풀면 그때까지의 취소는 다시 세지 않아요." />
      {release.isError && (
        <Notice tone="error" className="mb-3">
          {errorMessage(release.error)}
        </Notice>
      )}
      <Card>
        {query.isPending ? (
          <div className="flex flex-col gap-2 p-4">
            <Skeleton className="h-16" />
            <Skeleton className="h-16" />
          </div>
        ) : query.isError ? (
          <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
        ) : targets.length === 0 ? (
          <EmptyState title="검토할 회원이 없어요" description="본인 책임 취소가 90일 안에 4회가 되면 여기에 나와요." />
        ) : (
          <ul aria-label="정지 검토 대상" className="divide-y divide-neutral-100">
            {targets.map((target) => (
              <li key={`${target.userId}-${target.type}`} className="flex items-center justify-between gap-4 px-5 py-4">
                <div className="flex min-w-0 flex-col gap-1">
                  <span className="flex items-center gap-2">
                    {target.type && <Badge variant="warning">{SANCTION_TYPE_LABEL[target.type]}</Badge>}
                    <strong className="text-body font-semibold">{target.nickname ?? `회원 #${target.userId}`}</strong>
                  </span>
                  <span className="text-label text-neutral-500">
                    {target.type && SANCTION_CAUSE_LABEL[target.type]} <span className="num font-semibold text-neutral-900">{target.cancelCount}회</span>
                    {target.bannedUntil && ` · ${formatDateTime(target.bannedUntil)}까지 정지`}
                  </span>
                </div>
                {isReleasable(target) && (
                  <Button variant="outline" size="sm" onClick={() => setReleasing(target)}>
                    정지 풀기
                  </Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </Card>
      <ConfirmDialog
        open={releasing !== undefined}
        title="정지를 풀까요?"
        description={
          releasing && `${releasing.nickname ?? `회원 #${releasing.userId}`}님의 ${SANCTION_TYPE_LABEL[releasing.type]}를 지금 풀어요. 지금까지의 취소는 다시 세지 않아요.`
        }
        confirmLabel="풀기"
        loading={release.isPending}
        onConfirm={() => releasing && release.mutate(releasing)}
        onCancel={() => setReleasing(undefined)}
      />
    </div>
  );
}
