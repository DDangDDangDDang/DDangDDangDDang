import { Gift } from 'lucide-react';
import type { ChatMessage } from '@/api';
import { cn } from '@/lib/cn';
import { formatPoint } from '@/lib/format';
import { useDonationEffects } from '@/lib/use-donation-effects';

/**
 * 방송 화면 위 선물 이펙트: "누구님이 몇 P를 선물했어요" (+ 함께 보낸 한마디)
 * 시청 화면·판매자 스튜디오·작은 방송 창에서 그 화면의 채팅 메시지를 넘겨 쓴다. 여러 개면 차례로 보여 준다.
 */
export function DonationEffects({ messages, compact = false, className }: { messages: ChatMessage[]; compact?: boolean; className?: string }) {
  const effect = useDonationEffects(messages);
  return (
    <div aria-live="polite" className={cn('pointer-events-none', className)}>
      {effect && (
        <div
          key={effect.id}
          role="status"
          className={cn(
            'animate-donation-pop flex items-center gap-3 rounded-lg bg-brand-500 text-neutral-0 shadow-md',
            compact ? 'px-3 py-2' : 'max-w-[420px] px-4 py-3',
          )}
        >
          <span className={cn('flex shrink-0 items-center justify-center rounded-full bg-neutral-0/20', compact ? 'size-7' : 'size-10')}>
            <Gift className={compact ? 'size-4' : 'size-5'} aria-hidden="true" />
          </span>
          <span className="flex min-w-0 flex-col">
            <span className={cn('font-bold', compact ? 'text-label' : 'text-body-lg')}>
              {effect.nickname}님이 <span className="num">{formatPoint(effect.amount)}</span>를 선물했어요
            </span>
            {effect.message && !compact && <span className="truncate text-body text-neutral-0/90">{effect.message}</span>}
          </span>
        </div>
      )}
    </div>
  );
}
