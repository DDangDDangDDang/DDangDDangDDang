import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Gift, RefreshCw } from 'lucide-react';
import { useCallback, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { ApiError, api, call, errorMessage, isErrorCode, useSession, type ChatMessage, type Schema } from '@/api';
import { BidDialog } from '@/components/auction/BidDialog';
import { FeaturedAuctionSlot } from '@/components/auction/FeaturedAuctionOverlay';
import { useLoginGuard } from '@/components/auth/use-login-guard';
import { DonationDialog } from '@/components/donation/DonationDialog';
import { DonationEffects } from '@/components/donation/DonationEffects';
import { PowerSellerBadge } from '@/components/donation/PowerSellerBadge';
import { SellerIdentity } from '@/components/seller/SellerIdentity';
import { SubscribeButton } from '@/components/seller/SubscribeButton';
import { useSellerProfile } from '@/lib/use-seller';
import { Button, buttonVariants, EmptyState, ErrorState, Skeleton } from '@/components/ui';
import { formatDateTime, formatNumber } from '@/lib/format';
import { useLiveStreamAuctions, WATCH_AUCTION_REFRESH_MS } from '@/lib/use-live-stream-auctions';
import { AuctionPanel } from './AuctionPanel';
import { Hashtags } from '@/components/live/Hashtags';
import { LiveChat } from '@/components/live/LiveChat';
import { appendChatMessage } from '@/lib/live-room';
import { LiveVideo } from './LiveVideo';
import { useLiveRealtime } from '@/lib/use-live-realtime';
import { useWatchStream } from '@/components/watch/watch-context';

type LiveStream = Schema<'LiveStreamResponse'>;

/** 방송 카드에서 넘겨주는 값. 방송 상세 API에 판매자 닉네임이 없어서 카드의 값을 처음에 보여준다. */
export type LiveWatchLocationState = { sellerNickname?: string } | null;

/**
 * 방송 시청: 영상(LiveKit) + 방송 정보 + 이 방송의 경매 + 실시간 채팅(STOMP)
 * 로그인하지 않아도 영상과 채팅을 볼 수 있다. (손님 입장·손님 연결) 채팅 보내기·판매자 문의는 로그인 창을 띄운다.
 */
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
  const { guard, loginDialog } = useLoginGuard();
  const queryKey = ['live-stream', liveStreamId];
  const query = useQuery({
    queryKey,
    queryFn: () => call(api.GET('/api/v1/live-streams/{liveStreamId}', { params: { path: { liveStreamId } } })),
  });

  const refetchLiveStream = useCallback(
    () => void queryClient.invalidateQueries({ queryKey: ['live-stream', liveStreamId] }),
    [queryClient, liveStreamId],
  );

  // 입찰 창에 연 경매. 방송을 떠나지 않고 입찰한다. (입찰은 로그인해야 함)
  const [bidAuctionId, setBidAuctionId] = useState<number>();
  // 도네이션 선물 창 (로그인해야 함)
  const [giftOpen, setGiftOpen] = useState(false);

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
  // 방송 영상·소리는 앱 전체에서 이어 본다. 이 화면을 떠나도 방송 중이면 오른쪽 아래 작은 플레이어에서 이어지고, 돌아오면 다시 접속하지 않는다.
  const { video, muted, toggleSound } = useWatchStream({ liveStreamId, title: liveStream?.title, live: isLive }, refetchLiveStream);
  // 선물 효과음은 방송 소리를 켠 시청자에게만 낸다. (소리를 꺼 둔 사람에게 갑자기 소리가 나지 않게)
  const soundOn = !muted;

  // 판매자가 영상 위에 띄운 경매. 경매 목록은 옆 경매 카드와 같은 쿼리라 한 번만 불러온다.
  const auctions = useLiveStreamAuctions(liveStreamId, WATCH_AUCTION_REFRESH_MS);
  const openAuction = (auctionId: number, ongoing: boolean) => (ongoing ? guard(() => setBidAuctionId(auctionId)) : setBidAuctionId(auctionId));

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
          <LiveVideo
            liveStream={liveStream}
            video={video}
            muted={muted}
            onToggleSound={toggleSound}
            overlay={
              <>
                <FeaturedAuctionSlot
                  featuredAuctionId={liveStream.featuredAuctionId}
                  auctions={auctions.data ?? []}
                  onOpen={openAuction}
                  className="absolute bottom-4 left-4"
                />
                {/* 도네이션 선물 이펙트: 영상 오른쪽 위 (소리·전체 화면 버튼과 겹치지 않게) */}
                <DonationEffects messages={messages} sound={soundOn} className="absolute top-4 right-4" />
              </>
            }
          />
          <StreamInfo liveStream={liveStream} sellerNickname={sellerNickname} guard={guard} onGift={() => guard(() => setGiftOpen(true))} />
        </section>

        <aside aria-label="경매와 채팅" className="flex w-[380px] shrink-0 flex-col gap-4">
          <AuctionPanel liveStreamId={liveStreamId} onOpen={openAuction} />
          <LiveChat
            messages={messages}
            sellerId={liveStream.sellerId}
            notice={liveStream.notice}
            canChat={isLive}
            status={realtime.status}
            error={chatError}
            onSend={(content) => {
              setChatError(undefined);
              return realtime.sendChat(content);
            }}
            requireLogin={guard}
          />
        </aside>
      </div>
      {loginDialog}
      <BidDialog auctionId={bidAuctionId} onClose={() => setBidAuctionId(undefined)} />
      <DonationDialog liveStreamId={liveStreamId} sellerName={sellerNickname || '판매자'} open={giftOpen} onClose={() => setGiftOpen(false)} />
    </div>
  );
}

function StreamInfo({
  liveStream,
  sellerNickname,
  guard,
  onGift,
}: {
  liveStream: LiveStream;
  sellerNickname?: string;
  guard: (action: () => void) => void;
  /** 도네이션 선물 창 열기 (방송 중이고 내 방송이 아닐 때만 버튼이 보인다) */
  onGift: () => void;
}) {
  const session = useSession();
  const name = sellerNickname || '판매자';
  // 구독자 수·내 구독 여부 (판매자 이름 옆 SellerIdentity와 같은 캐시)
  const profile = useSellerProfile(liveStream.sellerId);
  const canAsk = liveStream.sellerId !== undefined && liveStream.sellerId !== session?.user.userId;
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
      <div className="flex flex-col gap-1">
        <h1 className="text-title-lg font-bold tracking-tight">{liveStream.title || '제목 없는 방송'}</h1>
        <Hashtags tags={liveStream.hashtags} />
      </div>
      <div className="flex items-center gap-3">
        {/* 판매자 인증 회원이면 인증 마크가 붙고, 누르면 판매자 프로필 창이 열린다. */}
        <SellerIdentity
          sellerId={liveStream.sellerId}
          fallbackName={name}
          detail={
            <>
              구독자 <span className="num font-semibold text-neutral-900">{formatNumber(profile.data?.subscriberCount ?? 0)}</span>명
              {time && ` · ${time}`}
            </>
          }
        />
        {profile.data?.powerSeller && <PowerSellerBadge />}
        {liveStream.sellerId !== undefined && <SubscribeButton sellerId={liveStream.sellerId} subscribed={profile.data?.subscribed ?? false} />}
        {/* 도네이션 선물은 방송 중인 판매자에게만, 내 방송에는 할 수 없다. */}
        {liveStream.status === 'LIVE' && canAsk && (
          <Button size="sm" className="ml-2" onClick={onGift}>
            <Gift className="size-4" aria-hidden="true" />
            선물하기
          </Button>
        )}
        {/* 로그인했으면 링크(옮겨 가도 방송은 작은 플레이어에서 이어짐).
            로그인하지 않았으면 로그인 창만 여는 버튼이고, 로그인 뒤 다시 누르면 문의가 열린다. (로그인 뒤에 새 탭을 열면 팝업으로 막힐 수 있어서) */}
        {canAsk &&
          (session ? (
            <Link to={`/messages/${liveStream.sellerId}`} className={buttonVariants({ variant: 'outline', size: 'sm', className: 'ml-2' })}>
              판매자에게 문의
            </Link>
          ) : (
            <Button variant="outline" size="sm" className="ml-2" onClick={() => guard(() => undefined)}>
              판매자에게 문의
            </Button>
          ))}
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
