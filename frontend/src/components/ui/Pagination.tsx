import { ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '@/lib/cn';
import { pageWindow } from '@/lib/pagination';

type Props = {
  /** 지금 페이지 (0부터) */
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
  /** 화면 읽기 프로그램에 읽히는 이름 (예: "경매 목록 페이지") */
  label: string;
};

/** 번호로 넘기는 목록의 페이지 이동 (경매 목록, 내 상품 등). 페이지가 하나뿐이면 그리지 않는다. */
export function Pagination({ page, totalPages, onChange, label }: Props) {
  if (totalPages <= 1) return null;
  const item = 'flex h-10 min-w-10 items-center justify-center rounded-md px-3 text-body transition-colors';

  return (
    <nav aria-label={label} className="mt-6 flex items-center justify-center gap-1.5">
      <button type="button" aria-label="이전 페이지" disabled={page <= 0} onClick={() => onChange(page - 1)} className={cn(item, 'border border-neutral-200 hover:bg-neutral-50 disabled:cursor-not-allowed disabled:opacity-40')}>
        <ChevronLeft className="size-4" aria-hidden="true" />
      </button>
      {pageWindow(page, totalPages).map((n) => (
        <button
          key={n}
          type="button"
          aria-current={n === page ? 'page' : undefined}
          onClick={() => onChange(n)}
          className={cn(item, 'num', n === page ? 'bg-neutral-900 font-bold text-neutral-0' : 'border border-neutral-200 hover:bg-neutral-50')}
        >
          {n + 1}
        </button>
      ))}
      <button
        type="button"
        aria-label="다음 페이지"
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
        className={cn(item, 'border border-neutral-200 hover:bg-neutral-50 disabled:cursor-not-allowed disabled:opacity-40')}
      >
        <ChevronRight className="size-4" aria-hidden="true" />
      </button>
    </nav>
  );
}
