import { useId, type ReactNode, type TextareaHTMLAttributes } from 'react';
import { cn } from '@/lib/cn';

export type TextareaProps = TextareaHTMLAttributes<HTMLTextAreaElement> & {
  /** 화면에 보이는 라벨. 필수 (접근성) */
  label: string;
  hideLabel?: boolean;
  /** 입력 아래 안내 문구 */
  hint?: ReactNode;
  /** 오류 문구. 있으면 테두리와 안내가 오류 표시로 바뀐다. */
  error?: string;
};

/** 여러 줄 입력 (방송 설명, 상품 설명 등). Input과 같은 모양이다. */
export function Textarea({ label, hideLabel = false, hint, error, id, className, ...props }: TextareaProps) {
  const autoId = useId();
  const textareaId = id ?? autoId;
  const describedBy = error ? `${textareaId}-error` : hint ? `${textareaId}-hint` : undefined;

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={textareaId} className={cn('text-label font-semibold text-neutral-900', hideLabel && 'sr-only')}>
        {label}
      </label>
      <textarea
        id={textareaId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={cn(
          'min-h-24 w-full resize-y rounded-md border border-neutral-400 bg-neutral-0 px-3.5 py-2.5 text-body text-neutral-900 placeholder:text-neutral-500',
          'disabled:bg-neutral-50 disabled:text-neutral-500',
          error && 'border-red-500',
          className,
        )}
        {...props}
      />
      {error ? (
        <p id={`${textareaId}-error`} className="text-caption text-red-300">
          {error}
        </p>
      ) : (
        hint && (
          <p id={`${textareaId}-hint`} className="text-caption text-neutral-500">
            {hint}
          </p>
        )
      )}
    </div>
  );
}
