import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, call, errorMessage, useSession, type Schema } from '@/api';
import { PageHeader } from '@/components/layout/PageHeader';
import { Badge, Button, Card, Dialog, EmptyState, ErrorState, Notice, Select, Skeleton, Textarea } from '@/components/ui';
import { formatDateTime } from '@/lib/format';
import { SELLER_APPLICATION_STATUS_LABEL, type SellerApplicationStatus } from '@/lib/labels';
import { useInfiniteScroll } from '@/lib/use-infinite-scroll';

type Application = Schema<'SellerApplicationResponse'>;

const PAGE_SIZE = 20;
const STATUS_OPTIONS = [
  { value: 'PENDING', label: '심사할 신청' },
  { value: 'APPROVED', label: '승인' },
  { value: 'REJECTED', label: '반려' },
  { value: '', label: '전체' },
];

/** 판매자 신청 심사 (관리자): 신청 목록 → 승인 또는 사유를 적어 반려 */
export function SellerApplicationsPage() {
  const session = useSession();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<SellerApplicationStatus | ''>('PENDING');
  const [rejecting, setRejecting] = useState<Application>();

  const query = useInfiniteQuery({
    queryKey: ['admin', 'seller-applications', status],
    queryFn: ({ pageParam }) =>
      call(api.GET('/api/v1/admin/seller-applications', { params: { query: { status: status || undefined, cursor: pageParam, size: PAGE_SIZE } } })),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last?.hasNext ? last.nextCursor : undefined),
    enabled: session?.user.role === 'ADMIN',
  });
  const sentinelRef = useInfiniteScroll(query.hasNextPage && !query.isError, query.isFetchingNextPage, query.fetchNextPage);
  const applications = query.data?.pages.flatMap((page) => page?.content ?? []) ?? [];

  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['admin', 'seller-applications'] });
  const approve = useMutation({
    mutationFn: (id: number) => call(api.POST('/api/v1/admin/seller-applications/{applicationId}/approve', { params: { path: { applicationId: id } } })),
    onSettled: refresh,
  });

  if (session?.user.role !== 'ADMIN') {
    return <EmptyState title="관리자만 쓸 수 있는 화면이에요" description="판매자 신청은 관리자가 심사해요." />;
  }

  return (
    <div className="mx-auto max-w-[960px]">
      <PageHeader title="판매자 심사" description="승인하면 그 회원에게 판매자 인증 마크와 판매자 프로필이 생겨요." />
      <div className="mb-4 w-[220px]">
        <Select label="보기" hideLabel options={STATUS_OPTIONS} value={status} onChange={(e) => setStatus(e.target.value as SellerApplicationStatus | '')} />
      </div>
      {approve.isError && (
        <Notice tone="error" className="mb-3">
          {errorMessage(approve.error)}
        </Notice>
      )}
      <Card aria-label="판매자 신청 목록">
        {query.isPending ? (
          <div className="flex flex-col gap-2 p-4">
            <Skeleton className="h-24" />
            <Skeleton className="h-24" />
          </div>
        ) : query.isError && applications.length === 0 ? (
          <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
        ) : applications.length === 0 ? (
          <EmptyState title="신청이 없어요" description="회원이 [내 정보 → 판매자 인증]에서 신청하면 여기에 나와요." />
        ) : (
          <ul className="divide-y divide-neutral-100">
            {applications.map((application) => (
              <li key={application.id} className="flex items-start justify-between gap-4 px-5 py-4">
                <div className="flex min-w-0 flex-col gap-1">
                  <span className="flex items-center gap-2">
                    {application.status && <Badge variant="outline">{SELLER_APPLICATION_STATUS_LABEL[application.status]}</Badge>}
                    <strong className="text-body font-semibold">{application.storeName}</strong>
                    <span className="text-label text-neutral-500">
                      {application.nickname || '-'} · {application.email || '-'}
                    </span>
                  </span>
                  <p className="text-body whitespace-pre-line text-neutral-700">{application.introduction}</p>
                  <span className="text-caption text-neutral-500">
                    {application.createdAt ? `${formatDateTime(application.createdAt)} 신청` : ''}
                    {application.reviewedAt ? ` · ${formatDateTime(application.reviewedAt)} 심사` : ''}
                    {application.rejectReason ? ` · 반려 사유: ${application.rejectReason}` : ''}
                  </span>
                </div>
                {application.status === 'PENDING' && (
                  <div className="flex shrink-0 gap-2">
                    <Button variant="outline" size="sm" onClick={() => setRejecting(application)}>
                      반려
                    </Button>
                    <Button size="sm" loading={approve.isPending && approve.variables === application.id} onClick={() => application.id && approve.mutate(application.id)}>
                      승인
                    </Button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
        <div ref={sentinelRef} />
        {query.isFetchingNextPage && <Skeleton className="m-4 h-24" />}
      </Card>
      <RejectDialog application={rejecting} onClose={() => setRejecting(undefined)} onRejected={refresh} />
    </div>
  );
}

function RejectDialog({ application, onClose, onRejected }: { application?: Application; onClose: () => void; onRejected: () => void }) {
  const [reason, setReason] = useState('');
  const reject = useMutation({
    mutationFn: () =>
      call(api.POST('/api/v1/admin/seller-applications/{applicationId}/reject', { params: { path: { applicationId: application?.id ?? 0 } }, body: { reason: reason.trim() } })),
    onSuccess: () => {
      setReason('');
      onRejected();
      onClose();
    },
  });

  return (
    <Dialog open={application !== undefined} title="신청 반려" description={`‘${application?.storeName ?? ''}’ 신청을 반려해요. 사유는 신청한 회원에게 보여요.`} onClose={onClose} closeDisabled={reject.isPending} className="w-[520px]">
      <div className="flex flex-col gap-3">
        <Textarea label="반려 사유" placeholder="예) 판매할 상품을 더 자세히 소개해 주세요." maxLength={200} rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />
        {reject.isError && <Notice tone="error">{errorMessage(reject.error)}</Notice>}
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose} disabled={reject.isPending}>
            닫기
          </Button>
          <Button disabled={!reason.trim()} loading={reject.isPending} onClick={() => reject.mutate()}>
            반려하기
          </Button>
        </div>
      </div>
    </Dialog>
  );
}
