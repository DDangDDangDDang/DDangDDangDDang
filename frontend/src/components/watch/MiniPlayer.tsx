import { Eye, Loader2, RefreshCw, Volume2, VolumeX, X } from 'lucide-react';
import { Link, matchPath, useLocation } from 'react-router';
import { useBroadcast } from '@/components/broadcast/broadcast-context';
import { Badge, buttonVariants } from '@/components/ui';
import { cn } from '@/lib/cn';
import { formatNumber } from '@/lib/format';
import { TrackAudio, TrackVideo } from './tracks';
import { useWatch } from './watch-context';

/**
 * 시청자 작은 플레이어: 방송을 보다가 다른 화면으로 가면 화면 오른쪽 아래에서 방송이 이어진다. (영상·소리)
 * 영상을 누르거나 [방송으로 돌아가기]를 누르면 시청 화면으로 돌아가고, [닫기]를 누르면 방송 연결을 끊는다.
 * 판매자 작은 방송 창(MiniBroadcast)이 떠 있으면 그 왼쪽에 둔다.
 */
export function MiniPlayer() {
  const { session, pageOpen, video, muted, toggleSound, ended, close } = useWatch();
  const broadcast = useBroadcast();
  const { pathname } = useLocation();
  // 시청 화면 경로에서는 숨긴다. 화면이 방송을 여는 effect가 돌기 전 첫 렌더에서 소리가 두 번 나지 않게.
  const onWatchPage = matchPath('/lives/:liveStreamId', pathname) !== null;
  if (!session || pageOpen || onWatchPage || (!session.live && !ended)) return null;

  const sellerBroadcastShown = broadcast.publishing && broadcast.liveStream !== undefined && pathname !== '/studio';
  const watchPath = `/lives/${session.liveStreamId}`;
  const title = session.title || '방송';
  const playing = !ended && video.phase === 'connected' && video.videoTrack;

  return (
    <aside
      aria-label="보던 방송"
      className={cn(
        'fixed bottom-6 z-40 w-[320px] overflow-hidden rounded-lg border border-neutral-100 bg-neutral-0 shadow-md',
        sellerBroadcastShown ? 'right-[340px]' : 'right-6',
      )}
    >
      <div className="flex items-center justify-between gap-2 px-3 py-2">
        <div className="flex min-w-0 items-center gap-2">
          {!ended && (
            <Badge variant="live">
              <span aria-hidden="true" className="size-1.5 rounded-full bg-neutral-0" />
              LIVE
            </Badge>
          )}
          <strong className="truncate text-label font-semibold" title={title}>
            {title}
          </strong>
        </div>
        <button
          type="button"
          aria-label="작은 플레이어 닫기"
          title="닫기 (방송 연결을 끊어요)"
          onClick={close}
          className="flex size-7 shrink-0 items-center justify-center rounded-md text-neutral-500 hover:bg-neutral-50"
        >
          <X className="size-4" aria-hidden="true" />
        </button>
      </div>

      <Link
        to={watchPath}
        aria-label={`${title} 시청 화면으로 돌아가기`}
        className="relative flex aspect-video items-center justify-center bg-stage text-neutral-400"
      >
        {playing && video.videoTrack && <TrackVideo track={video.videoTrack} />}
        {!ended && video.audioTracks.map((track) => <TrackAudio key={track.sid} track={track} muted={muted} />)}
        {!playing && <Status ended={ended} phase={video.phase} />}
        {!ended && video.phase === 'connected' && video.viewerCount !== undefined && (
          <Badge className="absolute top-2 left-2 bg-stage/70 font-normal text-neutral-0">
            <Eye className="size-3.5" aria-hidden="true" />
            <span className="num">{formatNumber(video.viewerCount)}</span>명 시청
          </Badge>
        )}
      </Link>

      <div className="flex items-center gap-1.5 px-3 py-2">
        {!ended && (
          <button
            type="button"
            aria-label={muted ? '소리 켜기' : '소리 끄기'}
            title={muted ? '소리 켜기' : '소리 끄기'}
            onClick={toggleSound}
            className="flex size-8 items-center justify-center rounded-md bg-neutral-50 text-neutral-700 hover:bg-neutral-100"
          >
            {muted ? <VolumeX className="size-4" aria-hidden="true" /> : <Volume2 className="size-4" aria-hidden="true" />}
          </button>
        )}
        {(video.phase === 'disconnected' || video.phase === 'error') && !ended && (
          <button type="button" onClick={video.retry} className="flex items-center gap-1 text-caption font-semibold text-brand-700 underline">
            <RefreshCw className="size-3.5" aria-hidden="true" />
            다시 연결
          </button>
        )}
        <Link to={watchPath} className={buttonVariants({ variant: 'secondary', size: 'sm', className: 'ml-auto shrink-0' })}>
          방송으로 돌아가기
        </Link>
      </div>
    </aside>
  );
}

function Status({ ended, phase }: { ended: boolean; phase: string }) {
  const text = ended
    ? '방송이 끝났어요'
    : phase === 'reconnecting'
      ? '연결이 끊겨 다시 연결하는 중이에요'
      : phase === 'disconnected' || phase === 'error'
        ? '방송 연결이 끊겼어요'
        : phase === 'connected'
          ? '판매자 화면을 기다리고 있어요'
          : '방송에 연결하는 중이에요';
  const spinning = !ended && (phase === 'joining' || phase === 'reconnecting');
  return (
    <span role="status" className="flex flex-col items-center gap-1 px-4 text-center text-caption">
      {spinning && <Loader2 className="size-5 animate-spin" aria-hidden="true" />}
      {text}
    </span>
  );
}
