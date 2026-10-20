import { AlertCircle, Inbox } from 'lucide-react';
import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '@/lib/cn';
import { Button } from './Button';

/**
 * 모든 목록·상세 화면은 세 가지 상태를 그린다. (frontend/CLAUDE.md)
 *  - 불러오는 중: Skeleton
 *  - 데이터 없음: EmptyState
 *  - 실패: ErrorState (다시 시도 버튼)
 */

/** 불러오는 동안 내용 자리에 보여주는 회색 상자 */
export function Skeleton({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div aria-hidden="true" className={cn('animate-pulse rounded-md bg-neutral-50', className)} {...props} />;
}

export function EmptyState({ title, description, action }: { title: string; description?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 px-6 py-12 text-center">
      <Inbox className="size-8 text-neutral-400" aria-hidden="true" />
      <p className="text-body-lg font-semibold">{title}</p>
      {description && <p className="text-body text-neutral-500">{description}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-center gap-2 px-6 py-12 text-center">
      <AlertCircle className="size-8 text-brand-500" aria-hidden="true" />
      <p className="text-body-lg font-semibold">{message}</p>
      {onRetry && (
        <Button variant="outline" size="sm" onClick={onRetry} className="mt-2">
          다시 시도
        </Button>
      )}
    </div>
  );
}
