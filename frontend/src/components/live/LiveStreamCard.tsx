import { CalendarClock, Eye, TrendingUp, Video } from 'lucide-react';
import { Link } from 'react-router';
import type { Schema } from '@/api';
import { Badge, Skeleton } from '@/components/ui';
import { formatDateTime, formatNumber } from '@/lib/format';
import { LIVE_STREAM_STATUS_LABEL } from '@/lib/labels';
import { Hashtags } from './Hashtags';
import { RecordedBadge } from './RecordedBadge';
import { PowerSellerBadge } from '@/components/donation/PowerSellerBadge';

type Card = Schema<'LiveStreamCard'>;

/** 방송 카드 (홈, 방송 목록). 누르면 방송 시청 화면으로 간다. 방송 썸네일은 아직 API에 없어 영상 자리만 그린다. */
export function LiveStreamCard({ card, showIncrease = false }: { card: Card; showIncrease?: boolean }) {
  const isLive = card.status === 'LIVE';

  return (
    <Link to={`/lives/${card.liveStreamId}`} state={{ sellerNickname: card.sellerNickname }} className="group flex flex-col gap-3 rounded-lg">
      <div className="relative flex aspect-video items-center justify-center overflow-hidden rounded-lg bg-stage text-neutral-500">
        <Video className="size-8" aria-hidden="true" />
        <div className="absolute top-3 left-3 flex gap-1.5">
          {card.status === 'LIVE' ? (
            <Badge variant="live">
              <span aria-hidden="true" className="size-1.5 rounded-full bg-neutral-0" />
              LIVE
            </Badge>
          ) : (
            card.status && <Badge variant="neutral">{LIVE_STREAM_STATUS_LABEL[card.status]}</Badge>
          )}
          {isLive && (
            <Badge className="bg-stage/70 font-semibold text-neutral-0">
              <Eye className="size-3.5" aria-hidden="true" />
              <span className="num">{formatNumber(card.viewerCount ?? 0)}</span>명 시청
            </Badge>
          )}
          {isLive && card.recorded && <RecordedBadge />}
          {card.powerSeller && <PowerSellerBadge tone="onVideo" />}
        </div>
        {isLive && showIncrease && (card.viewerIncrease ?? 0) > 0 && (
          <Badge variant="warning" className="absolute right-3 bottom-3">
            <TrendingUp className="size-3.5" aria-hidden="true" />
            <span className="num">+{formatNumber(card.viewerIncrease ?? 0)}</span>명
          </Badge>
        )}
      </div>
      <div className="flex flex-col gap-1">
        <strong className="line-clamp-2 text-body-lg font-semibold group-hover:underline">{card.title || '제목 없는 방송'}</strong>
        <span className="text-label text-neutral-500">{card.sellerNickname || '판매자'}</span>
        <StartTime card={card} />
        <Hashtags tags={card.hashtags} className="mt-0.5" />
      </div>
    </Link>
  );
}

function StartTime({ card }: { card: Card }) {
  if (card.status === 'SCHEDULED') {
    return (
      <span className="flex items-center gap-1 text-label font-semibold text-brand-700">
        <CalendarClock className="size-3.5" aria-hidden="true" />
        {card.scheduledStartAt ? `${formatDateTime(card.scheduledStartAt)} 시작 예정` : '시작 시간 미정'}
      </span>
    );
  }
  if (card.status === 'LIVE' && card.startedAt) {
    return <span className="text-label text-neutral-500">{formatDateTime(card.startedAt)} 시작</span>;
  }
  return null;
}

/** 방송 카드 자리 (불러오는 중) */
export function LiveStreamCardSkeleton() {
  return (
    <div className="flex flex-col gap-3">
      <Skeleton className="aspect-video rounded-lg" />
      <Skeleton className="h-5 w-4/5" />
      <Skeleton className="h-4 w-1/3" />
    </div>
  );
}
