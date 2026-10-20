import { useEffect, useRef, type ReactNode } from 'react';
import { Button } from './Button';

type ConfirmDialogProps = {
  open: boolean;
  title: string;
  description?: ReactNode;
  confirmLabel: string;
  cancelLabel?: string;
  /** 확인 요청 중이면 버튼에 스피너를 보여준다. */
  loading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
};

/**
 * 되돌릴 수 없는 동작(구매 확정 등) 전에 묻는 확인 창.
 * 브라우저의 confirm() 대신 쓴다. <dialog>라서 포커스 가두기·Esc 닫기가 기본으로 된다.
 */
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  cancelLabel = '취소',
  loading = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      aria-labelledby="confirm-dialog-title"
      onCancel={(event) => {
        event.preventDefault();
        if (!loading) onCancel();
      }}
      className="m-auto w-[420px] rounded-lg bg-neutral-0 p-6 text-neutral-900 shadow-md backdrop:bg-neutral-900/40"
    >
      <h2 id="confirm-dialog-title" className="text-title-md font-bold">
        {title}
      </h2>
      {description && <div className="mt-2 text-body text-neutral-700">{description}</div>}
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="outline" onClick={onCancel} disabled={loading}>
          {cancelLabel}
        </Button>
        <Button onClick={onConfirm} loading={loading}>
          {confirmLabel}
        </Button>
      </div>
    </dialog>
  );
}
