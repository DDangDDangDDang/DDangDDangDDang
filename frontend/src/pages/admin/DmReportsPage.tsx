import { useInfiniteQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api, call, errorMessage, useSession, type Schema } from '@/api';
import { PageHeader } from '@/components/layout/PageHeader';
import { Badge, Button, Card, EmptyState, ErrorState, Skeleton } from '@/components/ui';
import { cn } from '@/lib/cn';
import { flattenMessages } from '@/lib/dm';
import { formatDateTime } from '@/lib/format';
import { useInfiniteScroll } from '@/lib/use-infinite-scroll';

type Report = Schema<'DmReportResponse'>;

const PAGE_SIZE = 20;

/**
 * 1:1 채팅 신고 처리 (관리자): 최근 신고 목록 + 고른 신고의 대화방 메시지.
 * 서버는 신고가 들어온 대화방의 메시지만 보여 준다. 회원은 닉네임이 응답에 없어 회원 번호로 구분한다.
 */
export function DmReportsPage() {
  const session = useSession();
  const [selected, setSelected] = useState<Report>();

  const query = useInfiniteQuery({
    queryKey: ['admin', 'dm-reports'],
    queryFn: ({ pageParam }) => call(api.GET('/api/v1/admin/dm/reports', { params: { query: { cursor: pageParam, size: PAGE_SIZE } } })),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last?.hasNext ? last.nextCursor : undefined),
    enabled: session?.user.role === 'ADMIN',
  });
  const sentinelRef = useInfiniteScroll(query.hasNextPage, query.isFetchingNextPage, query.fetchNextPage);
  const reports = query.data?.pages.flatMap((page) => page?.content ?? []) ?? [];

  if (session?.user.role !== 'ADMIN') {
    return <EmptyState title="관리자만 쓸 수 있는 화면이에요" description="1:1 채팅 신고는 관리자가 확인해요." />;
  }

  return (
    <div>
      <PageHeader title="1:1 채팅 신고" description="신고된 메시지와 그 대화방의 앞뒤 대화를 확인해요." />
      <div className="flex items-start gap-4">
        <Card className="w-[440px] shrink-0" aria-label="신고 목록">
          {query.isPending ? (
            <div className="flex flex-col gap-2 p-4">
              {Array.from({ length: 4 }, (_, i) => (
                <Skeleton key={i} className="h-20" />
              ))}
            </div>
          ) : query.isError ? (
            <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
          ) : reports.length === 0 ? (
            <EmptyState title="들어온 신고가 없어요" description="회원이 1:1 채팅에서 신고하면 여기에 나와요." />
          ) : (
            <ul className="divide-y divide-neutral-100">
              {reports.map((report) => (
                <li key={report.reportId}>
                  <button
                    type="button"
                    aria-pressed={selected?.reportId === report.reportId}
                    onClick={() => setSelected(report)}
                    className={cn('flex w-full flex-col gap-1 px-5 py-3.5 text-left transition-colors', selected?.reportId === report.reportId ? 'bg-neutral-50' : 'hover:bg-neutral-25')}
                  >
                    <span className="flex items-center gap-2">
                      <Badge variant="warning">{report.reasonLabel || '신고'}</Badge>
                      <span className="text-caption text-neutral-500">{report.reportedAt ? formatDateTime(report.reportedAt) : ''}</span>
                    </span>
                    <span className="line-clamp-2 text-body">{report.reportedContent ? `“${report.reportedContent}”` : '메시지를 고르지 않은 신고'}</span>
                    <span className="text-caption text-neutral-500">
                      신고한 회원 {report.reporterId} → 신고된 회원 {report.reportedUserId}
                      {report.detail ? ` · ${report.detail}` : ''}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
          <div ref={sentinelRef} />
          {query.isFetchingNextPage && <Skeleton className="m-4 h-20" />}
        </Card>

        <Card className="min-w-0 flex-1" aria-label="신고된 대화">
          {selected?.reportId ? (
            <ReportMessages key={selected.reportId} report={selected as Report & { reportId: number }} />
          ) : (
            <EmptyState title="신고를 골라 주세요" description="왼쪽에서 신고를 누르면 그 대화방의 메시지가 나와요." />
          )}
        </Card>
      </div>
    </div>
  );
}

function ReportMessages({ report }: { report: Report & { reportId: number } }) {
  const reportId = report.reportId;
  const query = useInfiniteQuery({
    queryKey: ['admin', 'dm-reports', reportId, 'messages'],
    queryFn: ({ pageParam }) =>
      call(api.GET('/api/v1/admin/dm/reports/{reportId}/messages', { params: { path: { reportId }, query: { cursor: pageParam, size: 50 } } })),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last?.hasNext ? last.nextCursor : undefined),
  });
  const messages = flattenMessages(query.data?.pages.map((page) => page?.content) ?? []);

  if (query.isPending) return <Skeleton className="m-5 h-64" />;
  if (query.isError) return <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />;
  if (messages.length === 0) return <EmptyState title="대화방에 메시지가 없어요" />;

  return (
    <div className="flex flex-col gap-2 p-5">
      {query.hasNextPage && (
        <Button variant="outline" size="sm" className="self-center" loading={query.isFetchingNextPage} onClick={() => void query.fetchNextPage()}>
          이전 메시지 보기
        </Button>
      )}
      <ol className="flex flex-col gap-2">
        {messages.map((message) => {
          const byReported = message.senderId === report.reportedUserId;
          const reported = message.messageId === report.messageId;
          return (
            <li key={message.messageId} className={cn('flex flex-col gap-0.5', byReported ? 'items-start' : 'items-end')}>
              <span className="text-caption text-neutral-500">
                {byReported ? `신고된 회원 ${message.senderId}` : `신고한 회원 ${message.senderId}`} · {message.sentAt ? formatDateTime(message.sentAt) : ''}
              </span>
              <p
                className={cn(
                  'max-w-[75%] rounded-lg px-3.5 py-2 text-body break-words whitespace-pre-wrap',
                  reported ? 'border-2 border-brand-500 bg-brand-50' : byReported ? 'bg-neutral-50' : 'bg-neutral-100',
                )}
              >
                {reported && <span className="mr-1 font-bold text-brand-700">[신고된 메시지]</span>}
                {message.content}
              </p>
            </li>
          );
        })}
      </ol>
    </div>
  );
}
