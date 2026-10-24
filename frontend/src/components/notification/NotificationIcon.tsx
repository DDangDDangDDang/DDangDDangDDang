import { Ban, Clock, Gavel, Gift, Hourglass, PackageCheck, Radio, TrendingUp, Trophy, type LucideIcon } from 'lucide-react';
import type { NotificationMessage } from '@/api';
import { cn } from '@/lib/cn';

type NotificationType = NonNullable<NotificationMessage['type']>;

const ICONS: Record<NotificationType, LucideIcon> = {
  LIVE_STARTED: Radio,
  AUCTION_STARTED: Gavel,
  AUCTION_OUTBID: TrendingUp,
  AUCTION_CLOSING_SOON: Clock,
  AUCTION_CANCELED: Ban,
  AUCTION_WON: Trophy,
  AUCTION_SOLD: PackageCheck,
  AUCTION_FAILED: Gavel,
  AUCTION_ENDED: Gavel,
  DONATION_POINT_EARNED: Gift,
  DONATION_POINT_EXPIRING: Hourglass,
};

/** 알림 종류별 동그란 아이콘. 서둘러야 하는 알림(방송 시작·상위 입찰·마감 임박·낙찰)은 강조색 */
export function NotificationIcon({ type }: { type?: NotificationType }) {
  const Icon = (type && ICONS[type]) || Gavel;
  const urgent = type === 'LIVE_STARTED' || type === 'AUCTION_OUTBID' || type === 'AUCTION_CLOSING_SOON' || type === 'AUCTION_WON';
  return (
    <span
      className={cn(
        'mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-full',
        urgent ? 'bg-brand-500 text-white' : 'bg-neutral-50 text-neutral-700',
      )}
    >
      <Icon className="size-4" aria-hidden="true" />
    </span>
  );
}
