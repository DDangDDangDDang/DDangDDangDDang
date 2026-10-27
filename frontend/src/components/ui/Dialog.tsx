import { X } from 'lucide-react';
import { useId, useLayoutEffect, useRef, type ReactNode } from 'react';
import { cn } from '@/lib/cn';

type DialogProps = {
  open: boolean;
  title: string;
  description?: ReactNode;
  /** 닫기(X, Esc). 저장 중처럼 닫으면 안 될 때는 closeDisabled */
  onClose: () => void;
  closeDisabled?: boolean;
  /**
   * 잠깐 숨긴다. 내용(입력값·상태)은 그대로 두고 화면에서만 내린다.
   * <dialog>는 맨 위 층(top layer)에 떠서 z-index로도 덮을 수 없고 바깥을 클릭할 수 없게 막는다.
   * 그래서 이 창에서 외부 결제창(포트원 등)을 띄울 때는 그동안 숨겨야 결제창을 쓸 수 있다.
   */
  suspended?: boolean;
  children: ReactNode;
  className?: string;
};

/**
 * 화면을 옮기지 않고 입력을 받는 창 (방송 중 상품 등록 등). 되돌릴 수 없는 동작 확인은 ConfirmDialog를 쓴다.
 * <dialog>라서 포커스 가두기·Esc 닫기가 기본으로 된다.
 */
export function Dialog({ open, title, description, onClose, closeDisabled = false, suspended = false, children, className }: DialogProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const descriptionId = useId();

  // 화면에 그리기 전에 열고 닫는다. 숨김(suspended)이 바로 반영돼야 그 직후 띄우는 외부 창을 가리지 않는다.
  useLayoutEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    const visible = open && !suspended;
    if (visible && !dialog.open) dialog.showModal();
    if (!visible && dialog.open) dialog.close();
  }, [open, suspended]);

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      aria-describedby={description ? descriptionId : undefined}
      onCancel={(event) => {
        event.preventDefault();
        if (!closeDisabled) onClose();
      }}
      className={cn('m-auto max-h-[90vh] w-[720px] overflow-y-auto rounded-lg bg-neutral-0 p-6 text-neutral-900 shadow-md backdrop:bg-black/60', className)}
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
