import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Eye, Pencil, RefreshCw } from 'lucide-react';
import { useCallback, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { api, call, errorMessage, isErrorCode, type Schema } from '@/api';
import { ACTIVE_STREAM_KEY, START_STREAM_KEY, useBroadcast, useStudioPresence } from '@/components/broadcast/broadcast-context';
import { IdleWarning } from '@/components/broadcast/IdleWarning';
import { PageHeader } from '@/components/layout/PageHeader';
import { LiveChat } from '@/components/live/LiveChat';
import { Badge, Button, Card, CardBody, ConfirmDialog, EmptyState, ErrorState, Notice, Skeleton } from '@/components/ui';
import { formatDateTime, formatNumber } from '@/lib/format';
import { LiveAuctionProducts, LiveAuctionStatus } from './LiveAuctionPanel';
import { ChatNoticeEditor } from './ChatNoticeEditor';
import { ModerationPanel } from './ModerationPanel';
import { StreamForm } from './StreamForm';
import { StudioStage } from './StudioStage';
import { useLiveAuctions } from './use-live-auctions';

type LiveStream = Schema<'LiveStreamResponse'>;

/**
 * 판매자 스튜디오: 방송 만들기·수정 → 카메라·마이크 미리보기 → 방송 시작(LiveKit 송출) → 채팅·AI 검토 알림 → 방송 종료.
 * 종료되지 않은 방송이 없으면 방송 만들기를 보여준다.
 * 송출·채팅 연결은 앱 전체(BroadcastProvider)에 있어 이 화면을 떠나도 방송이 이어진다. 이 화면은 그 상태를 보여주고 조작한다.
 */
export function SellerStudioPage() {
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: ACTIVE_STREAM_KEY,
    queryFn: () => call(api.GET('/api/v1/live-streams/me/active')),
  });
  /** 방금 종료한 방송 제목. 방송 만들기 위에 안내로 보여준다. */
  const [endedTitle, setEndedTitle] = useState<string>();

  const reload = useCallback(() => void queryClient.invalidateQueries({ queryKey: ACTIVE_STREAM_KEY }), [queryClient]);

  if (query.isPending) return <StudioSkeleton />;
  if (query.isError) return <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />;

  const liveStream = query.data;
  if (!liveStream?.id || liveStream.status === 'ENDED') {
    return (
      <NoActiveStream
        endedTitle={endedTitle}
        onCreated={(created) => {
          setEndedTitle(undefined);
          queryClient.setQueryData(ACTIVE_STREAM_KEY, created);
        }}
        onActiveExists={reload}
      />
    );
  }

  return (
    <Studio
      key={liveStream.id}
      liveStream={liveStream as LiveStream & { id: number }}
      onChange={(next) => queryClient.setQueryData(ACTIVE_STREAM_KEY, next)}
      onEnded={(ended) => {
        setEndedTitle(ended.title || '방송');
        queryClient.setQueryData(ACTIVE_STREAM_KEY, null);
      }}
      reload={reload}
    />
  );
}

/** 데이터 없음: 종료되지 않은 방송이 없으면 새 방송을 만든다. */
function NoActiveStream({ endedTitle, onCreated, onActiveExists }: { endedTitle?: string; onCreated: (liveStream: LiveStream) => void; onActiveExists: () => void }) {
  const { idle } = useBroadcast();
  return (
    <div className="mx-auto flex max-w-[720px] flex-col gap-4">
      <PageHeader title="판매자 스튜디오" description="방송을 만들고 카메라를 확인한 뒤 바로 시작할 수 있어요." />
      {endedTitle && <Notice tone="success">‘{endedTitle}’ 방송을 종료했어요. 시청자 연결이 끊기고 채팅에 종료 안내가 나갔어요.</Notice>}
      {!endedTitle && idle.autoEnded && (
        <Notice tone="error">‘{idle.autoEnded}’ 방송은 송출이 30분 동안 없어 자동으로 종료됐어요. 진행 중인 경매는 그대로 이어져요.</Notice>
      )}
      <Card>
        <EmptyState title="진행 중이거나 예정된 방송이 없어요" description="새 방송을 만들면 여기서 미리보기를 확인하고 방송을 시작할 수 있어요. 방송은 한 번에 하나만 열 수 있어요." />
        <CardBody className="border-t border-neutral-100 pt-5">
          <h2 className="mb-4 text-title-md font-bold">새 방송 만들기</h2>
          <StreamForm mode="create" onSaved={onCreated} onActiveExists={onActiveExists} />
        </CardBody>
      </Card>
    </div>
  );
}

type StudioProps = {
  liveStream: LiveStream & { id: number };
  onChange: (liveStream: LiveStream) => void;
  onEnded: (liveStream: LiveStream) => void;
  reload: () => void;
};

function Studio({ liveStream, onChange, onEnded, reload }: StudioProps) {
  const liveStreamId = liveStream.id;
  const isLive = liveStream.status === 'LIVE';
  const [editing, setEditing] = useState(false);
  const [confirmEnd, setConfirmEnd] = useState(false);

  // 카메라·마이크, 송출(LiveKit), 채팅·검토 알림(STOMP)은 앱 전체에서 이어진다. (BroadcastProvider)
  useStudioPresence();
  const { media, publisher, chat, alerts, dismissAlert, idle } = useBroadcast();
  const flaggedIds = useMemo(() => new Set(alerts.map((a) => a.messageId)), [alerts]);
  // 방송 중 경매
  const liveAuctions = useLiveAuctions(liveStreamId);

  const start = useMutation({
    mutationKey: START_STREAM_KEY,
    mutationFn: () => call(api.POST('/api/v1/live-streams/{liveStreamId}/start', { params: { path: { liveStreamId } } })),
    onSuccess: (token) => {
      publisher.handOffToken(token);
      // 시작 응답에는 방송 정보가 없으므로 상태만 먼저 바꾸고(송출 접속 시작) 정확한 정보는 다시 불러온다.
      onChange({ ...liveStream, status: 'LIVE' });
      reload();
    },
    onError: (error) => {
      // 다른 창에서 이미 시작했거나 끝냈으면 지금 상태를 다시 불러온다.
      if (isErrorCode(error, 'LIVESTREAM_003')) reload();
    },
  });

  const end = useMutation({
    mutationFn: () => call(api.POST('/api/v1/live-streams/{liveStreamId}/end', { params: { path: { liveStreamId } } })),
    onSuccess: (ended) => {
      setConfirmEnd(false);
      onEnded(ended ?? liveStream);
    },
    onError: (error) => {
      if (isErrorCode(error, 'LIVESTREAM_004')) {
        setConfirmEnd(false);
        reload();
      }
    },
  });

  const noDevice = !media.camera.track && !media.microphone.track && !media.camera.starting && !media.microphone.starting;

  return (
    <div className="flex flex-col gap-4">
      {isLive && chat.status === 'reconnecting' && (
        <div role="status" className="flex items-center gap-2.5 rounded-md bg-neutral-900 px-4 py-2.5 text-label text-neutral-0">
          <RefreshCw className="size-4 animate-spin" aria-hidden="true" />
          <span>
            <strong className="font-semibold">채팅 연결이 끊겨 다시 연결하는 중이에요.</strong> 송출은 그대로 이어져요.
          </span>
        </div>
      )}

      {isLive && idle.endsAt && <IdleWarning endsAt={idle.endsAt} />}

      <div className="flex items-start gap-6">
        <section aria-labelledby="studio-title" className="flex min-w-0 flex-1 flex-col gap-4">
          <StudioHeader liveStream={liveStream} viewerCount={isLive ? publisher.viewerCount : undefined} editing={editing} onEdit={() => setEditing(true)} />

          {editing && (
            <Card aria-label="방송 정보 수정">
              <CardBody className="pt-5">
                <StreamForm
                  mode="edit"
                  liveStream={liveStream}
                  onSaved={(saved) => {
                    onChange(saved);
                    setEditing(false);
                  }}
                  onCancel={() => setEditing(false)}
                />
              </CardBody>
            </Card>
          )}

          <StudioStage
            live={isLive}
            camera={media.camera}
            microphone={media.microphone}
            publisher={publisher}
            onToggle={(kind) => void media.toggle(kind)}
            onSelectDevice={(kind, deviceId) => void media.selectDevice(kind, deviceId)}
          />

          <div className="flex flex-col gap-3 rounded-lg border border-neutral-100 bg-neutral-0 px-5 py-4">
            {isLive ? (
              <div className="flex items-center gap-4">
                <Button variant="outline" size="lg" onClick={() => setConfirmEnd(true)}>
                  방송 종료
                </Button>
                <span className="text-label text-neutral-500">종료하면 시청자 연결이 끊기고 채팅에 종료 안내가 나가요. 종료한 방송은 다시 시작할 수 없어요.</span>
              </div>
            ) : (
              <div className="flex items-center gap-4">
                <Button size="lg" loading={start.isPending} disabled={noDevice} onClick={() => start.mutate()}>
                  방송 시작
                </Button>
                <span className="text-label text-neutral-500">
                  {noDevice ? '카메라나 마이크를 하나 이상 켜야 방송을 시작할 수 있어요.' : '시작하면 방송 목록에 LIVE로 나오고 시청자가 바로 볼 수 있어요.'}
                </span>
              </div>
            )}
            {start.isError && <Notice tone="error">{errorMessage(start.error)}</Notice>}
          </div>

          <LiveAuctionProducts live={isLive} auctions={liveAuctions} />
        </section>

        <aside aria-label="경매 현황과 채팅" className="flex w-[380px] shrink-0 flex-col gap-4">
          <LiveAuctionStatus auctions={liveAuctions} />
          <ModerationPanel alerts={alerts} onDismiss={dismissAlert} />
          <ChatNoticeEditor liveStream={liveStream} onSaved={onChange} />
          <LiveChat
            messages={chat.messages}
            sellerId={liveStream.sellerId}
            notice={liveStream.notice}
            canChat={isLive}
            status={chat.status}
            error={chat.error}
            flaggedIds={flaggedIds}
            onSend={chat.send}
          />
        </aside>
      </div>

      <ConfirmDialog
        open={confirmEnd}
        title="방송을 종료할까요?"
        description={
          <div className="flex flex-col gap-2">
            <p>시청자 연결이 끊기고 채팅에 종료 안내가 나가요. 종료한 방송은 다시 시작할 수 없어요.</p>
            {end.isError && <Notice tone="error">{errorMessage(end.error)}</Notice>}
          </div>
        }
        confirmLabel="방송 종료"
        loading={end.isPending}
        onConfirm={() => end.mutate()}
        onCancel={() => {
          setConfirmEnd(false);
          end.reset();
        }}
      />
    </div>
  );
}

function StudioHeader({ liveStream, viewerCount, editing, onEdit }: { liveStream: LiveStream; viewerCount?: number; editing: boolean; onEdit: () => void }) {
  const isLive = liveStream.status === 'LIVE';
  const time =
    isLive && liveStream.startedAt
      ? `${formatDateTime(liveStream.startedAt)} 시작`
      : liveStream.scheduledStartAt
        ? `${formatDateTime(liveStream.scheduledStartAt)} 시작 예정`
        : '시작 예정 시간 없음';

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center justify-between gap-4">
        <div className="flex min-w-0 items-center gap-2.5">
          <h1 id="studio-title" className="truncate text-title-lg font-bold tracking-tight">
            {liveStream.title || '제목 없는 방송'}
          </h1>
          {isLive ? (
            <Badge variant="live" className="h-6">
              방송 중
            </Badge>
          ) : (
            <Badge variant="outline" className="h-6">
              방송 전
            </Badge>
          )}
        </div>
        {!editing && (
          <Button variant="outline" size="sm" onClick={onEdit}>
            <Pencil className="size-4" aria-hidden="true" />
            정보 수정
          </Button>
        )}
      </div>
      <div className="flex items-center gap-3 text-label text-neutral-500">
        {isLive && (
          <span className="flex items-center gap-1">
            <Eye className="size-4" aria-hidden="true" />
            <span>
              시청 <span className="num font-semibold text-neutral-900">{viewerCount === undefined ? '-' : formatNumber(viewerCount)}</span>명
            </span>
          </span>
        )}
        <span>{time}</span>
        {isLive && (
          <Link to={`/lives/${liveStream.id}`} target="_blank" rel="noreferrer" className="text-info-700 underline-offset-2 hover:underline">
            시청 화면 열기
          </Link>
        )}
      </div>
      {liveStream.description && !editing && <p className="text-body whitespace-pre-line text-neutral-700">{liveStream.description}</p>}
    </div>
  );
}

function StudioSkeleton() {
  return (
    <div className="flex items-start gap-6" aria-busy="true">
      <div className="flex min-w-0 flex-1 flex-col gap-4">
        <Skeleton className="h-8 w-2/3" />
        <Skeleton className="h-5 w-64" />
        <Skeleton className="aspect-video rounded-lg" />
        <Skeleton className="h-20 rounded-lg" />
      </div>
      <div className="flex w-[380px] shrink-0 flex-col gap-4">
        <Skeleton className="h-40 rounded-lg" />
        <Skeleton className="h-[520px] rounded-lg" />
      </div>
    </div>
  );
}
