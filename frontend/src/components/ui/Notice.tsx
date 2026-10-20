import { AlertCircle, CheckCircle2, Info } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';

const STYLES = {
  /** 처리 결과 성공 (인증 완료, 저장 완료) */
  success: { box: 'bg-info-50 text-info-700', Icon: CheckCircle2 },
  /** 오류·주의 (서버 오류 문구) */
  error: { box: 'bg-brand-50 text-brand-700', Icon: AlertCircle },
  /** 일반 안내 (메일을 보냈습니다, 대기 중 등) */
  info: { box: 'bg-neutral-50 text-neutral-700', Icon: Info },
} as const;

/** 폼·영역 안에 보여주는 한 줄 안내. 오류는 role="alert", 나머지는 role="status"로 읽힌다. */
export function Notice({ tone = 'info', children, className }: { tone?: keyof typeof STYLES; children: ReactNode; className?: string }) {
  const { box, Icon } = STYLES[tone];
  return (
    <p role={tone === 'error' ? 'alert' : 'status'} className={cn('flex items-start gap-2 rounded-md px-3 py-2.5 text-label', box, className)}>
      <Icon className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <span>{children}</span>
    </p>
  );
}
