import { ChevronDown } from 'lucide-react';
import { useId, type SelectHTMLAttributes } from 'react';
import { cn } from '@/lib/cn';

export type SelectProps = SelectHTMLAttributes<HTMLSelectElement> & {
  /** 화면에 보이는 라벨. 필수 (접근성) */
  label: string;
  hideLabel?: boolean;
  options: { value: string; label: string }[];
  error?: string;
};

/** 선택 상자 (택배사, 목록 필터 등). 브라우저 기본 select를 토큰 스타일로 감쌌다. */
export function Select({ label, hideLabel = false, options, error, id, className, ...props }: SelectProps) {
  const autoId = useId();
  const selectId = id ?? autoId;

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={selectId} className={cn('text-label font-semibold text-neutral-900', hideLabel && 'sr-only')}>
        {label}
      </label>
      <div className="relative">
        <select
          id={selectId}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${selectId}-error` : undefined}
          className={cn(
            'h-11 w-full appearance-none rounded-md border border-neutral-400 bg-neutral-0 pr-10 pl-3.5 text-body text-neutral-900',
            error && 'border-red-500',
            className,
          )}
          {...props}
        >
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <ChevronDown aria-hidden="true" className="pointer-events-none absolute top-1/2 right-3.5 size-4 -translate-y-1/2 text-neutral-500" />
      </div>
      {error && (
        <p id={`${selectId}-error`} className="text-caption text-red-300">
          {error}
        </p>
      )}
    </div>
  );
}
