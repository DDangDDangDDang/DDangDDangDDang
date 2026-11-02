import { useInfiniteQuery } from '@tanstack/react-query';
import { useState, type ReactNode, type Ref } from 'react';
import { api, call, errorMessage, useSession, type Schema } from '@/api';
import { PageHeader } from '@/components/layout/PageHeader';
import { Badge, Button, Card, EmptyState, ErrorState, Skeleton } from '@/components/ui';
import { cn } from '@/lib/cn';
import { flattenMessages } from '@/lib/dm';
import { formatDateTime } from '@/lib/format';
import { useInfiniteScroll } from '@/lib/use-infinite-scroll';

type Report = Schema<'DmReportResponse'>;
type ModerationFlag = Schema<'DmModerationFlagResponse'>;
type Tab = 'reports' | 'ai';

const PAGE_SIZE = 20;

/** 목록에서 고른 건: 회원 신고(report) 또는 AI 검토 표시(flag). 오른쪽 대화방 보기에 필요한 값만 담는다. */
type Selected =
  | { kind: 'report'; id: number; reportedUserId?: number; messageId?: number }
  | { kind: 'ai'; id: number; reportedUserId?: number; messageId?: number };

/**
 * 1:1 채팅 신고 처리 (관리자): 회원 신고 목록, AI 검토가 표시한 메시지 목록 + 고른 건의 대화방 메시지.
 * 서버는 신고나 AI 표시가 있는 대화방의 메시지만 보여 준다. 회원은 닉네임이 응답에 없어 회원 번호로 구분한다.
 */
export function DmReportsPage() {
  const session = useSession();
  const [tab, setTab] = useState<Tab>('reports');
  const [selected, setSelected] = useState<Selected>();
  const isAdmin = session?.user.role === 'ADMIN';

  if (!isAdmin) {
    return <EmptyState title="관리자만 쓸 수 있는 화면이에요" description="1:1 채팅 신고는 관리자가 확인해요." />;
  }

  const changeTab = (next: Tab) => {
    setTab(next);
    setSelected(undefined);
  };

  return (
    <div>
      <PageHeader title="1:1 채팅 신고" description="신고되거나 AI가 확인을 요청한 메시지와 그 대화방의 앞뒤 대화를 확인해요." />
      <div className="mb-3 flex gap-2" role="group" aria-label="목록 고르기">
        <Button variant={tab === 'reports' ? 'primary' : 'outline'} size="sm" aria-pressed={tab === 'reports'} onClick={() => changeTab('reports')}>
          회원 신고
        </Button>
        <Button variant={tab === 'ai' ? 'primary' : 'outline'} size="sm" aria-pressed={tab === 'ai'} onClick={() => changeTab('ai')}>
          AI 검토
        </Button>
      </div>
      <div className="flex items-start gap-4">
        <Card className="w-[440px] shrink-0" aria-label={tab === 'reports' ? '신고 목록' : 'AI 검토 목록'}>
          {tab === 'reports' ? (
            <ReportList selectedId={selected?.kind === 'report' ? selected.id : undefined} onSelect={setSelected} />
          ) : (
            <FlagList selectedId={selected?.kind === 'ai' ? selected.id : undefined} onSelect={setSelected} />
          )}
        </Card>

        <Card className="min-w-0 flex-1" aria-label="대화방 메시지">
          {selected ? (
            <RoomMessages key={`${selected.kind}-${selected.id}`} selected={selected} />
          ) : (
            <EmptyState title="왼쪽에서 하나를 골라 주세요" description="고르면 그 대화방의 메시지가 나와요." />
          )}
        </Card>
      </div>
    </div>
  );
}

function ReportList({ selectedId, onSelect }: { selectedId?: number; onSelect: (selected: Selected) => void }) {
  const query = useInfiniteQuery({
    queryKey: ['admin', 'dm-reports'],
    queryFn: ({ pageParam }) => call(api.GET('/api/v1/admin/dm/reports', { params: { query: { cursor: pageParam, size: PAGE_SIZE } } })),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last?.hasNext ? last.nextCursor : undefined),
  });
  // 다음 페이지를 못 불러오면 자동으로 다시 요청하지 않는다. ([다시 시도]로)
  const sentinelRef = useInfiniteScroll(query.hasNextPage && !query.isError, query.isFetchingNextPage, query.fetchNextPage);
  const reports: Report[] = query.data?.pages.flatMap((page) => page?.content ?? []) ?? [];

  return (
    <ListFrame
      pending={query.isPending}
      error={query.isError ? errorMessage(query.error) : undefined}
      onRetry={() => void query.refetch()}
      empty={reports.length === 0 && <EmptyState title="들어온 신고가 없어요" description="회원이 1:1 채팅에서 신고하면 여기에 나와요." />}
      sentinelRef={sentinelRef}
      fetchingNext={query.isFetchingNextPage}
    >
      {reports.map((report) => (
        <ListItem
          key={report.reportId}
          active={report.reportId !== undefined && report.reportId === selectedId}
          onClick={() => report.reportId !== undefined && onSelect({ kind: 'report', id: report.reportId, reportedUserId: report.reportedUserId, messageId: report.messageId })}
          badge={report.reasonLabel || '신고'}
          at={report.reportedAt}
          content={report.reportedContent ? `“${report.reportedContent}”` : '메시지를 고르지 않은 신고'}
          detail={`신고한 회원 ${report.reporterId} → 신고된 회원 ${report.reportedUserId}${report.detail ? ` · ${report.detail}` : ''}`}
        />
      ))}
    </ListFrame>
  );
}

function FlagList({ selectedId, onSelect }: { selectedId?: number; onSelect: (selected: Selected) => void }) {
  const query = useInfiniteQuery({
    queryKey: ['admin', 'dm-moderation-flags'],
    queryFn: ({ pageParam }) => call(api.GET('/api/v1/admin/dm/moderation-flags', { params: { query: { cursor: pageParam, size: PAGE_SIZE } } })),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last?.hasNext ? last.nextCursor : undefined),
  });
  const sentinelRef = useInfiniteScroll(query.hasNextPage && !query.isError, query.isFetchingNextPage, query.fetchNextPage);
  const flags: ModerationFlag[] = query.data?.pages.flatMap((page) => page?.content ?? []) ?? [];

  return (
    <ListFrame
      pending={query.isPending}
      error={query.isError ? errorMessage(query.error) : undefined}
      onRetry={() => void query.refetch()}
      empty={flags.length === 0 && <EmptyState title="AI가 확인을 요청한 메시지가 없어요" description="AI 검토가 외부 거래 유도·욕설 같은 1:1 메시지를 찾으면 여기에 나와요." />}
      sentinelRef={sentinelRef}
      fetchingNext={query.isFetchingNextPage}
    >
      {flags.map((flag) => (
        <ListItem
          key={flag.flagId}
          active={flag.flagId !== undefined && flag.flagId === selectedId}
          onClick={() => flag.flagId !== undefined && onSelect({ kind: 'ai', id: flag.flagId, reportedUserId: flag.senderId, messageId: flag.messageId })}
          badge={flag.categoryLabel || 'AI 검토'}
          at={flag.flaggedAt}
          content={flag.content ? `“${flag.content}”` : '메시지를 찾을 수 없어요'}
          detail={`보낸 회원 ${flag.senderId}${flag.reason ? ` · ${flag.reason}` : ''}`}
        />
      ))}
    </ListFrame>
  );
}

type ListFrameProps = {
  pending: boolean;
  error?: string;
  onRetry: () => void;
  empty: ReactNode;
  sentinelRef: Ref<HTMLDivElement>;
  fetchingNext: boolean;
  children: ReactNode;
};

function ListFrame({ pending, error, onRetry, empty, sentinelRef, fetchingNext, children }: ListFrameProps) {
  return (
    <>
      {pending ? (
        <div className="flex flex-col gap-2 p-4">
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} className="h-20" />
          ))}
        </div>
      ) : error ? (
        <ErrorState message={error} onRetry={onRetry} />
      ) : (
        empty || <ul className="divide-y divide-neutral-100">{children}</ul>
      )}
      <div ref={sentinelRef} />
      {fetchingNext && <Skeleton className="m-4 h-20" />}
    </>
  );
}

type ListItemProps = { active: boolean; onClick: () => void; badge: string; at?: string; content: string; detail: string };

function ListItem({ active, onClick, badge, at, content, detail }: ListItemProps) {
  return (
    <li>
      <button
        type="button"
        aria-pressed={active}
        onClick={onClick}
        className={cn('flex w-full flex-col gap-1 px-5 py-3.5 text-left transition-colors', active ? 'bg-neutral-50' : 'hover:bg-neutral-25')}
      >
        <span className="flex items-center gap-2">
          <Badge variant="warning">{badge}</Badge>
          <span className="text-caption text-neutral-500">{at ? formatDateTime(at) : ''}</span>
        </span>
        <span className="line-clamp-2 text-body">{content}</span>
        <span className="text-caption text-neutral-500">{detail}</span>
      </button>
    </li>
  );
}

function RoomMessages({ selected }: { selected: Selected }) {
  const { kind, id } = selected;
  const query = useInfiniteQuery({
    queryKey: ['admin', kind === 'report' ? 'dm-reports' : 'dm-moderation-flags', id, 'messages'],
    queryFn: ({ pageParam }) =>
      kind === 'report'
        ? call(api.GET('/api/v1/admin/dm/reports/{reportId}/messages', { params: { path: { reportId: id }, query: { cursor: pageParam, size: 50 } } }))
        : call(api.GET('/api/v1/admin/dm/moderation-flags/{flagId}/messages', { params: { path: { flagId: id }, query: { cursor: pageParam, size: 50 } } })),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last?.hasNext ? last.nextCursor : undefined),
  });
  const messages = flattenMessages(query.data?.pages.map((page) => page?.content) ?? []);

  if (query.isPending) return <Skeleton className="m-5 h-64" />;
  if (query.isError) return <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />;
  if (messages.length === 0) return <EmptyState title="대화방에 메시지가 없어요" />;

  const targetLabel = kind === 'report' ? '[신고된 메시지]' : '[AI 확인 요청]';
  return (
    <div className="flex flex-col gap-2 p-5">
      {query.hasNextPage && (
        <Button variant="outline" size="sm" className="self-center" loading={query.isFetchingNextPage} onClick={() => void query.fetchNextPage()}>
          이전 메시지 보기
        </Button>
      )}
      <ol className="flex flex-col gap-2">
        {messages.map((message) => {
          const byReported = message.senderId === selected.reportedUserId;
          const target = message.messageId === selected.messageId;
          const sender = kind === 'report' ? (byReported ? '신고된 회원' : '신고한 회원') : byReported ? '보낸 회원' : '상대 회원';
          return (
            <li key={message.messageId} className={cn('flex flex-col gap-0.5', byReported ? 'items-start' : 'items-end')}>
              <span className="text-caption text-neutral-500">
                {sender} {message.senderId} · {message.sentAt ? formatDateTime(message.sentAt) : ''}
              </span>
              <p
                className={cn(
                  'max-w-[75%] rounded-lg px-3.5 py-2 text-body break-words whitespace-pre-wrap',
                  target ? 'border-2 border-brand-500 bg-brand-50' : byReported ? 'bg-neutral-50' : 'bg-neutral-100',
                )}
              >
                {target && <span className="mr-1 font-bold text-brand-700">{targetLabel}</span>}
                {message.content}
              </p>
            </li>
          );
        })}
      </ol>
    </div>
  );
}
