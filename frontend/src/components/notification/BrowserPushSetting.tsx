import { BellOff, BellRing } from 'lucide-react';
import { Button, Notice } from '@/components/ui';
import { cn } from '@/lib/cn';
import type { useWebPush } from '@/lib/web-push';

/** 브라우저 알림 켜기·끄기. 지원하지 않는 브라우저면 그리지 않는다. */
export function BrowserPushSetting({ push, className }: { push: ReturnType<typeof useWebPush>; className?: string }) {
  if (push.state === 'unsupported') return null;
  return (
    <div className={cn('flex flex-col gap-2', className)}>
      <div className="flex items-center justify-between gap-3">
        <span className="flex min-w-0 flex-1 items-center gap-2 text-label text-neutral-700">
          {push.state === 'on' ? (
            <BellRing className="size-4 shrink-0 text-brand-500" aria-hidden="true" />
          ) : (
            <BellOff className="size-4 shrink-0" aria-hidden="true" />
          )}
          {push.state === 'on' ? '브라우저 알림 켜짐 · 탭을 닫아도 알려 드려요' : '브라우저 알림 · 탭을 닫아도 알려 드려요'}
        </span>
        {push.state === 'on' ? (
          <Button variant="ghost" size="sm" className="shrink-0" loading={push.pending} onClick={() => void push.disable()}>
            끄기
          </Button>
        ) : (
          push.state === 'off' && (
            <Button variant="outline" size="sm" className="shrink-0" loading={push.pending} onClick={() => void push.enable()}>
              켜기
            </Button>
          )
        )}
      </div>
      {push.state === 'denied' && <Notice tone="info">이 사이트의 알림이 차단돼 있어요. 주소창 왼쪽 아이콘 → 알림을 허용으로 바꾼 뒤 새로고침해 주세요.</Notice>}
      {push.error && <Notice tone="error">{push.error}</Notice>}
    </div>
  );
}
