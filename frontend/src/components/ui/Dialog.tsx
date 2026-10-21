import { X } from 'lucide-react';
import { useEffect, useId, useRef, type ReactNode } from 'react';
import { cn } from '@/lib/cn';

type DialogProps = {
  open: boolean;
  title: string;
  description?: ReactNode;
  /** 닫기(X, Esc). 저장 중처럼 닫으면 안 될 때는 closeDisabled */
  onClose: () => void;
  closeDisabled?: boolean;
  children: ReactNode;
  className?: string;
};

/**
 * 화면을 옮기지 않고 입력을 받는 창 (방송 중 상품 등록 등). 되돌릴 수 없는 동작 확인은 ConfirmDialog를 쓴다.
 * <dialog>라서 포커스 가두기·Esc 닫기가 기본으로 된다.
 */
export function Dialog({ open, title, description, onClose, closeDisabled = false, children, className }: DialogProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const descriptionId = useId();

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      aria-describedby={description ? descriptionId : undefined}
      onCancel={(event) => {
        event.preventDefault();
        if (!closeDisabled) onClose();
      }}
      className={cn('m-auto max-h-[90vh] w-[720px] overflow-y-auto rounded-lg bg-neutral-0 p-6 text-neutral-900 shadow-md backdrop:bg-neutral-900/40', className)}
    >
      <div className="mb-4 flex items-start justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h2 id={titleId} className="text-title-md font-bold">
            {title}
          </h2>
          {description && (
            <div id={descriptionId} className="text-body text-neutral-500">
              {description}
            </div>
          )}
        </div>
        <button
          type="button"
          aria-label="닫기"
          disabled={closeDisabled}
          onClick={onClose}
          className="flex size-9 shrink-0 items-center justify-center rounded-md text-neutral-500 hover:bg-neutral-50 disabled:opacity-50"
        >
          <X className="size-5" aria-hidden="true" />
        </button>
      </div>
      {open && children}
    </dialog>
  );
}
