import { TimerOff } from 'lucide-react';
import { cn } from '@/lib/cn';
import { seoulTimeMillis } from '@/lib/datetime-input';
import { formatRemaining } from '@/lib/format';
import { useNow } from '@/lib/use-now';

/**
 * 송출이 오래 없어 곧 방송이 자동으로 끝난다는 안내 (스튜디오, 작은 방송 창).
 * 서버가 카메라·마이크 송출을 다시 확인하면(최대 1분) 사라진다.
 * 화면 낭독기는 처음 한 번만 읽도록 매초 바뀌는 남은 시간은 알림(role="alert") 밖에 둔다.
 */
export function IdleWarning({ endsAt, compact = false }: { endsAt: string; compact?: boolean }) {
  const now = useNow(1000);
  const left = Math.max(0, Math.floor((seoulTimeMillis(endsAt) - now.getTime()) / 1000));

  return (
    <div className={cn('flex items-start gap-2.5 bg-brand-50 text-brand-700', compact ? 'px-3 py-2 text-caption' : 'rounded-md px-4 py-3 text-label')}>
      <TimerOff className={cn('shrink-0', compact ? 'mt-px size-3.5' : 'mt-0.5 size-4')} aria-hidden="true" />
      <p>
        <strong role="alert" className="font-semibold">
          송출이 없어 곧 방송이 자동으로 종료돼요.
        </strong>{' '}
        <span className="font-semibold">
          남은 시간 <span className="num">{formatRemaining(left)}</span>
        </span>
        {' · '}카메라나 마이크를 켜면 방송이 이어져요.
      </p>
    </div>
  );
}
