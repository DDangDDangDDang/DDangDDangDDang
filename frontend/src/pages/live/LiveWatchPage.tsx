import { useQuery, useQueryClient } from '@tanstack/react-query';
import { RefreshCw } from 'lucide-react';
import { useCallback, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { ApiError, api, call, errorMessage, isErrorCode, type ChatMessage, type Schema } from '@/api';
import { buttonVariants, EmptyState, ErrorState, Skeleton } from '@/components/ui';
import { formatDateTime } from '@/lib/format';
import { AuctionPanel } from './AuctionPanel';
import { LiveChat } from './LiveChat';
import { appendChatMessage } from './live-room';
import { LiveVideo } from './LiveVideo';
import { useLiveRealtime } from './use-live-realtime';
import { useLiveVideo } from './use-live-video';

type LiveStream = Schema<'LiveStreamResponse'>;

/** 방송 카드에서 넘겨주는 값. 방송 상세 API에 판매자 닉네임이 없어서 카드의 값을 처음에 보여준다. */
export type LiveWatchLocationState = { sellerNickname?: string } | null;

/** 방송 시청: 영상(LiveKit) + 방송 정보 + 경매 패널 자리 + 실시간 채팅(STOMP) */
export function LiveWatchPage() {
  const params = useParams();
  const liveStreamId = Number(params.liveStreamId);

  if (!Number.isInteger(liveStreamId) || liveStreamId <= 0) {
    return <NotFound />;
  }
  // 다른 방송으로 이동하면 채팅·영상 상태를 새로 시작한다.
  return <LiveWatch key={liveStreamId} liveStreamId={liveStreamId} />;
}

function LiveWatch({ liveStreamId }: { liveStreamId: number }) {
  const queryClient = useQueryClient();
  const queryKey = ['live-stream', liveStreamId];
  const query = useQuery({
    queryKey,
    queryFn: () => call(api.GET('/api/v1/live-streams/{liveStreamId}', { params: { path: { liveStreamId } } })),
  });

  const refetchLiveStream = useCallback(
    () => void queryClient.invalidateQueries({ queryKey: ['live-stream', liveStreamId] }),
    [queryClient, liveStreamId],
  );

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [chatError, setChatError] = useState<string>();

  const realtime = useLiveRealtime(liveStreamId, {
    onChat: (message) => {
      setMessages((list) => appendChatMessage(list, message));
      // 서버 안내(방송 시작·종료 등)가 오면 방송 상태가 바뀌었을 수 있으므로 다시 불러온다.
      if (message.type === 'SYSTEM') refetchLiveStream();
    },
    onUpdated: (message) => queryClient.setQueryData(queryKey, message.liveStream),
    onError: (error) => setChatError(errorMessage(new ApiError(error.code ?? '', error.message ?? '', 0))),
  });

  const liveStream = query.data;
  const isLive = liveStream?.status === 'LIVE';
  const video = useLiveVideo(liveStreamId, isLive, refetchLiveStream);

  const location = useLocation();
  const sellerNickname = video.sellerName ?? (location.state as LiveWatchLocationState)?.sellerNickname;

  if (query.isPending) return <WatchSkeleton />;
  if (query.isError) {
    if (isErrorCode(query.error, 'LIVESTREAM_001')) return <NotFound />;
    return <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />;
  }
  if (!liveStream) return <NotFound />;

  return (
    <div className="flex flex-col gap-4">
      {realtime.status === 'reconnecting' && (
        <div role="status" className="flex items-center gap-2.5 rounded-md bg-neutral-900 px-4 py-2.5 text-label text-neutral-0">
          <RefreshCw className="size-4 animate-spin" aria-hidden="true" />
          <span>
            <strong className="font-semibold">연결이 끊겨 다시 연결하는 중이에요.</strong> 채팅은 연결되면 바로 다시 할 수 있어요.
          </span>
        </div>
      )}

      <div className="flex items-start gap-6">
        <section aria-label="방송" className="flex min-w-0 flex-1 flex-col gap-5">
          <LiveVideo liveStream={liveStream} video={video} />
          <StreamInfo liveStream={liveStream} sellerNickname={sellerNickname} />
        </section>

        <aside aria-label="경매와 채팅" className="flex w-[380px] shrink-0 flex-col gap-4">
          <AuctionPanel />
          <LiveChat
            messages={messages}
            sellerId={liveStream.sellerId}
            canChat={isLive}
            status={realtime.status}
            error={chatError}
            onSend={(content) => {
              setChatError(undefined);
              return realtime.sendChat(content);
            }}
          />
        </aside>
      </div>
    </div>
  );
}

function StreamInfo({ liveStream, sellerNickname }: { liveStream: LiveStream; sellerNickname?: string }) {
  const name = sellerNickname || '판매자';
  const time =
    liveStream.status === 'LIVE' && liveStream.startedAt
      ? `${formatDateTime(liveStream.startedAt)} 시작`
      : liveStream.status === 'ENDED' && liveStream.endedAt
        ? `${formatDateTime(liveStream.endedAt)} 종료`
        : liveStream.scheduledStartAt
          ? `${formatDateTime(liveStream.scheduledStartAt)} 시작 예정`
          : null;

  return (
    <div className="flex flex-col gap-3">
      <h1 className="text-title-lg font-bold tracking-tight">{liveStream.title || '제목 없는 방송'}</h1>
      <div className="flex items-center gap-3">
        <span aria-hidden="true" className="flex size-10 items-center justify-center rounded-full bg-brand-100 font-bold text-brand-700">
          {name.slice(0, 1)}
        </span>
        <div className="flex flex-col">
          <strong className="text-body-lg font-semibold">{name}</strong>
          {time && <span className="text-label text-neutral-500">{time}</span>}
        </div>
      </div>
      {liveStream.description && (
        <p className="rounded-lg border border-neutral-100 bg-neutral-0 px-5 py-4 text-body whitespace-pre-line text-neutral-700">
          {liveStream.description}
        </p>
      )}
    </div>
  );
}

function WatchSkeleton() {
  return (
    <div className="flex items-start gap-6" aria-busy="true">
      <div className="flex min-w-0 flex-1 flex-col gap-5">
        <Skeleton className="aspect-video rounded-lg" />
        <Skeleton className="h-8 w-2/3" />
        <Skeleton className="h-10 w-48" />
      </div>
      <div className="flex w-[380px] shrink-0 flex-col gap-4">
        <Skeleton className="h-48 rounded-lg" />
        <Skeleton className="h-[520px] rounded-lg" />
      </div>
    </div>
  );
}

function NotFound() {
  return (
    <EmptyState
      title="방송을 찾을 수 없어요"
      description="주소가 바뀌었거나 삭제된 방송이에요."
      action={
        <Link to="/" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
          홈으로
        </Link>
      }
    />
  );
}
