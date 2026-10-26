import { CalendarClock, Eye, TrendingUp } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router';
import type { Schema } from '@/api';
import { SellerAvatar } from '@/components/seller/SellerAvatar';
import { Badge, Skeleton } from '@/components/ui';
import { cn } from '@/lib/cn';
import { formatDateTime, formatNumber, formatRemaining } from '@/lib/format';
import { LIVE_STREAM_STATUS_LABEL } from '@/lib/labels';
import { useNow } from '@/lib/use-now';
import { Hashtags } from './Hashtags';
import { RecordedBadge } from './RecordedBadge';
import { PowerSellerBadge } from '@/components/donation/PowerSellerBadge';

type Card = Schema<'LiveStreamCard'>;

/**
 * 방송 카드 (홈, 방송 목록). 누르면 방송 시청 화면으로 간다.
 * 썸네일 위에 LIVE·시청자 수·방송 시간을 겹쳐 보여 주고, 아래에 판매자 프로필·제목·해시태그를 둔다.
 * 방송 썸네일은 아직 API에 없어 판매자 이름 첫 글자를 크게 그린다.
 */
export function LiveStreamCard({ card, showIncrease = false }: { card: Card; showIncrease?: boolean }) {
  const isLive = card.status === 'LIVE';
  const seller = card.sellerNickname || '판매자';

  return (
    <Link to={`/lives/${card.liveStreamId}`} state={{ sellerNickname: card.sellerNickname }} className="group flex min-w-0 flex-col gap-3 rounded-lg">
      <LiveThumbnail name={seller} className="transition-shadow group-hover:ring-2 group-hover:ring-brand-500 group-hover:ring-offset-2 group-hover:ring-offset-neutral-25">
        <div className="absolute top-2.5 left-2.5 flex gap-1.5">
          {isLive ? (
            <Badge variant="live">LIVE</Badge>
          ) : (
            card.status && <Badge variant="neutral">{LIVE_STREAM_STATUS_LABEL[card.status]}</Badge>
          )}
          {isLive && (
            <Badge className="bg-stage/70 font-semibold text-white">
              <Eye className="size-3.5" aria-hidden="true" />
              <span className="num">{formatNumber(card.viewerCount ?? 0)}</span>
              <span>명</span>
            </Badge>
          )}
          {isLive && card.recorded && <RecordedBadge />}
          {card.powerSeller && <PowerSellerBadge tone="onVideo" />}
        </div>
        {isLive && showIncrease && (card.viewerIncrease ?? 0) > 0 && (
          <Badge variant="warning" className="absolute bottom-2.5 left-2.5">
            <TrendingUp className="size-3.5" aria-hidden="true" />
            <span className="num">+{formatNumber(card.viewerIncrease ?? 0)}</span>
            <span>명</span>
          </Badge>
        )}
        {isLive && card.startedAt && <Elapsed startedAt={card.startedAt} />}
      </LiveThumbnail>
      <div className="flex min-w-0 gap-2.5">
        <SellerAvatar name={seller} size="md" />
        <div className="flex min-w-0 flex-col gap-0.5">
          <strong className="line-clamp-2 text-body-lg leading-snug font-semibold group-hover:text-white">{card.title || '제목 없는 방송'}</strong>
          <span className="text-label text-neutral-500">{seller}</span>
          <ScheduledTime card={card} />
          <Hashtags tags={card.hashtags} className="mt-0.5" />
        </div>
      </div>
    </Link>
  );
}

/**
 * 방송 썸네일 자리. 실제 썸네일이 생기기 전까지 판매자 이름 첫 글자를 어두운 바탕에 크게 그린다.
 * 같은 판매자는 늘 같은 바탕색이 나오도록 이름으로 색을 고른다.
 */
export function LiveThumbnail({ name, className, children }: { name: string; className?: string; children?: ReactNode }) {
  const tone = THUMB_TONES[[...name].reduce((sum, ch) => sum + (ch.codePointAt(0) ?? 0), 0) % THUMB_TONES.length];
  return (
    <div className={cn('relative flex aspect-video items-center justify-center overflow-hidden rounded-lg', tone, className)}>
      <span aria-hidden="true" className="text-display font-bold text-neutral-900/25">
        {[...name][0]}
      </span>
      {children}
    </div>
  );
}

/** 썸네일 바탕: 카드 색보다 조금 밝은 어두운 색들 (브랜드·정보·중립 계열) */
const THUMB_TONES = ['bg-brand-50', 'bg-info-50', 'bg-neutral-50', 'bg-brand-100'];

/** 썸네일 오른쪽 아래 방송 시간 (시작부터 지금까지, 매초 갱신) */
function Elapsed({ startedAt }: { startedAt: string }) {
  const now = useNow(1000);
  return (
    <Badge className="absolute right-2.5 bottom-2.5 bg-stage/70 font-semibold text-white">
      <span className="sr-only">방송 시간</span>
      <span className="num">{formatRemaining((now.getTime() - new Date(startedAt).getTime()) / 1000)}</span>
    </Badge>
  );
}

function ScheduledTime({ card }: { card: Card }) {
  if (card.status !== 'SCHEDULED') return null;
  return (
    <span className="flex items-center gap-1 text-label font-semibold text-brand-700">
      <CalendarClock className="size-3.5" aria-hidden="true" />
      {card.scheduledStartAt ? `${formatDateTime(card.scheduledStartAt)} 시작 예정` : '시작 시간 미정'}
    </span>
  );
}

/** 방송 카드 자리 (불러오는 중) */
export function LiveStreamCardSkeleton() {
  return (
    <div className="flex flex-col gap-3">
      <Skeleton className="aspect-video rounded-lg" />
      <div className="flex gap-2.5">
        <Skeleton className="size-10 shrink-0 rounded-full" />
        <div className="flex flex-1 flex-col gap-1.5">
          <Skeleton className="h-5 w-4/5" />
          <Skeleton className="h-4 w-1/3" />
        </div>
      </div>
    </div>
  );
}
