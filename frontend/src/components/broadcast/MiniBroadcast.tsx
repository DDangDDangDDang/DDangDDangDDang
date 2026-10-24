import type { LocalVideoTrack } from 'livekit-client';
import { ChevronDown, ChevronUp, Eye, Mic, MicOff, Video, VideoOff } from 'lucide-react';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useLocation } from 'react-router';
import { Badge, buttonVariants } from '@/components/ui';
import { cn } from '@/lib/cn';
import { formatNumber } from '@/lib/format';
import { useBroadcast } from './broadcast-context';
import { AuctionEffects } from '@/components/auction/AuctionEffects';
import { DonationEffects } from '@/components/donation/DonationEffects';
import { useStudioDonationSound } from '@/lib/donation-sound';
import { IdleWarning } from './IdleWarning';

/** 송출 연결 상태 문구 (송출 중이 아닐 때만) */
const PHASE_TEXT: Partial<Record<string, string>> = {
  idle: '송출 준비 중',
  connecting: '방송 서버에 연결하는 중',
  reconnecting: '연결이 끊겨 다시 연결하는 중',
  disconnected: '송출이 끊겼어요. 스튜디오에서 다시 연결해 주세요.',
  error: '송출하지 못했어요. 스튜디오에서 다시 연결해 주세요.',
  replaced: '다른 창에서 송출 중이에요.',
};

/**
 * 방송 중에 스튜디오 밖 화면에 띄우는 작은 방송 창 (오른쪽 아래).
 * 송출은 앱 전체에서 이어지므로, 판매자가 상품 등록·경매 목록 등을 보는 동안 내 화면과 시청자 수를 확인하고
 * 마이크·카메라를 끄거나 스튜디오로 돌아갈 수 있다.
 */
export function MiniBroadcast() {
  const { publishing, liveStream, media, publisher, unseen, idle, chat } = useBroadcast();
  // 스튜디오에서 정한 선물 효과음 설정을 따른다.
  const [donationSound] = useStudioDonationSound();
  const location = useLocation();
  const [folded, setFolded] = useState(false);
  if (!publishing || !liveStream || location.pathname === '/studio') return null;

  const { camera, microphone } = media;
  const cameraOn = camera.enabled && !!camera.track;
  const micOn = microphone.enabled && !!microphone.track;
  const phaseText = publisher.phase === 'connected' ? undefined : PHASE_TEXT[publisher.phase];

  return (
    <aside aria-label="방송 중" className="fixed right-6 bottom-6 z-40 w-[300px] overflow-hidden rounded-lg border border-neutral-100 bg-neutral-0 shadow-md">
      {idle.endsAt && <IdleWarning endsAt={idle.endsAt} compact />}
      <div className="flex items-center justify-between gap-2 px-3 py-2">
        <div className="flex min-w-0 items-center gap-2">
          <Badge variant="live">
            <span aria-hidden="true" className="size-1.5 rounded-full bg-neutral-0" />
            LIVE
          </Badge>
          <strong className="truncate text-label font-semibold">{liveStream.title || '방송 중'}</strong>
        </div>
        <button
          type="button"
          aria-label={folded ? '방송 창 펼치기' : '방송 창 접기'}
          aria-expanded={!folded}
          onClick={() => setFolded(!folded)}
          className="flex size-7 shrink-0 items-center justify-center rounded-md text-neutral-500 hover:bg-neutral-50"
        >
          {folded ? <ChevronUp className="size-4" aria-hidden="true" /> : <ChevronDown className="size-4" aria-hidden="true" />}
        </button>
      </div>

      {!folded && (
        <>
          <div className="relative flex aspect-video items-center justify-center bg-stage text-neutral-400">
            {camera.track && <SmallPreview track={camera.track} hidden={!cameraOn} />}
            {!cameraOn && (
              <span className="flex flex-col items-center gap-1 text-caption">
                <VideoOff className="size-5" aria-hidden="true" />
                카메라가 꺼져 있어요
              </span>
            )}
            {publisher.viewerCount !== undefined && (
              <Badge className="absolute top-2 left-2 bg-stage/70 font-normal text-white">
                <Eye className="size-3.5" aria-hidden="true" />
                <span className="num">{formatNumber(publisher.viewerCount)}</span>명 시청
              </Badge>
            )}
            {/* 다른 화면에 가 있어도 도네이션 선물이 오면 작게 알려 준다. */}
            <DonationEffects messages={chat.messages} compact sound={donationSound} className="absolute inset-x-2 bottom-2 flex justify-center" />
            {/* 입찰·낙찰도 작게 알려 준다. */}
            <AuctionEffects messages={chat.messages} compact sound={donationSound} className="absolute inset-0" />
          </div>
          {phaseText && (
            <p role="status" className="bg-brand-50 px-3 py-1.5 text-caption text-brand-700">
              {phaseText}
            </p>
          )}
        </>
      )}

      <div className="flex items-center gap-1.5 px-3 py-2">
        <MiniToggle on={micOn} label={micOn ? '마이크 끄기' : '마이크 켜기'} disabled={microphone.starting} onClick={() => void media.toggle('microphone')}>
          {micOn ? <Mic className="size-4" aria-hidden="true" /> : <MicOff className="size-4" aria-hidden="true" />}
        </MiniToggle>
        <MiniToggle on={cameraOn} label={cameraOn ? '카메라 끄기' : '카메라 켜기'} disabled={camera.starting} onClick={() => void media.toggle('camera')}>
          {cameraOn ? <Video className="size-4" aria-hidden="true" /> : <VideoOff className="size-4" aria-hidden="true" />}
        </MiniToggle>
        {(unseen.chats > 0 || unseen.alerts > 0) && (
          <span className="ml-1 truncate text-caption text-neutral-500">
            {unseen.chats > 0 && (
              <>
                새 채팅 <span className="num font-semibold text-neutral-900">{formatNumber(unseen.chats)}</span>
              </>
            )}
            {unseen.chats > 0 && unseen.alerts > 0 && ' · '}
            {unseen.alerts > 0 && (
              <span className="font-semibold text-brand-700">
                검토 알림 <span className="num">{formatNumber(unseen.alerts)}</span>
              </span>
            )}
          </span>
        )}
        <Link to="/studio" className={buttonVariants({ variant: 'secondary', size: 'sm', className: 'ml-auto shrink-0' })}>
          스튜디오로 돌아가기
        </Link>
      </div>
    </aside>
  );
}

function SmallPreview({ track, hidden }: { track: LocalVideoTrack; hidden: boolean }) {
  const ref = useRef<HTMLVideoElement>(null);
  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    track.attach(element);
    return () => {
      track.detach(element);
    };
  }, [track]);
  return <video ref={ref} aria-label="내 카메라 화면" className={cn('absolute inset-0 size-full object-contain', hidden && 'invisible')} autoPlay playsInline muted />;
}

function MiniToggle({ on, label, disabled, onClick, children }: { on: boolean; label: string; disabled: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      disabled={disabled}
      onClick={onClick}
      className={cn(
        'flex size-9 shrink-0 items-center justify-center rounded-md transition-colors disabled:cursor-not-allowed disabled:opacity-60',
        on ? 'bg-neutral-50 text-neutral-900 hover:bg-neutral-100' : 'bg-brand-500 text-white hover:bg-brand-600',
      )}
    >
      {children}
    </button>
  );
}
