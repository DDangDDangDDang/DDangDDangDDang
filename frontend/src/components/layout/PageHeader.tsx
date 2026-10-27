import type { ReactNode } from 'react';

/** 화면 제목 + 설명 + 오른쪽 동작 버튼 */
export function PageHeader({ title, description, actions }: { title: string; description?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex items-end justify-between gap-4">
      <div className="flex flex-col gap-1">
        <h1 className="text-title-lg font-bold tracking-tight">{title}</h1>
        {description && <p className="text-body text-neutral-500">{description}</p>}
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  );
}

/** 로그인·회원가입처럼 가운데 좁은 카드로 보여주는 화면의 틀 */
export function AuthCard({ title, description, children, footer }: { title: string; description?: ReactNode; children: ReactNode; footer?: ReactNode }) {
  return (
    <div className="mx-auto mt-6 w-[440px]">
      <section className="rounded-lg border border-neutral-100 bg-neutral-0 p-8 shadow-sm">
        <h1 className="text-title-lg font-bold tracking-tight">{title}</h1>
        {description && <p className="mt-1 text-body text-neutral-500">{description}</p>}
        <div className="mt-6 flex flex-col gap-4">{children}</div>
      </section>
      {footer && <div className="mt-4 flex justify-center gap-4 text-label text-neutral-500">{footer}</div>}
    </div>
  );
}
