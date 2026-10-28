import { useId, type InputHTMLAttributes, type ReactNode } from 'react';
import { cn } from '@/lib/cn';

export type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  /** 화면에 보이는 라벨. 필수 (접근성) */
  label: string;
  /** 라벨을 시각적으로 숨긴다. (채팅 입력처럼 자리가 좁을 때) */
  hideLabel?: boolean;
  /** 입력 아래 안내 문구 */
  hint?: ReactNode;
  /** 오류 문구. 있으면 테두리와 안내가 오류 표시로 바뀐다. */
  error?: string;
  /** 입력 오른쪽 끝에 붙는 단위 (예: "P") */
  suffix?: string;
};

export function Input({ label, hideLabel = false, hint, error, suffix, id, className, ...props }: InputProps) {
  const autoId = useId();
  const inputId = id ?? autoId;
  const describedBy = error ? `${inputId}-error` : hint ? `${inputId}-hint` : undefined;

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={inputId} className={cn('text-label font-semibold text-neutral-900', hideLabel && 'sr-only')}>
        {label}
      </label>
      <div className="relative">
        <input
          id={inputId}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={cn(
            'h-11 w-full rounded-md border border-neutral-400 bg-neutral-0 px-3.5 text-body text-neutral-900 placeholder:text-neutral-500',
            'disabled:bg-neutral-50 disabled:text-neutral-500',
            error && 'border-red-500',
            suffix && 'pr-10',
            className,
          )}
          {...props}
        />
        {suffix && (
          <span aria-hidden="true" className="pointer-events-none absolute inset-y-0 right-3.5 flex items-center font-semibold text-neutral-500">
            {suffix}
          </span>
        )}
      </div>
      {error ? (
        <p id={`${inputId}-error`} className="text-caption text-red-300">
          {error}
        </p>
      ) : (
        hint && (
          <p id={`${inputId}-hint`} className="text-caption text-neutral-500">
            {hint}
          </p>
        )
      )}
    </div>
  );
}
