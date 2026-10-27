import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Pencil, Plus, Trash2 } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { api, call, errorMessage, useSession, type Schema } from '@/api';
import { PageHeader } from '@/components/layout/PageHeader';
import { Button, Card, CardBody, ConfirmDialog, EmptyState, ErrorState, Input, Notice, Skeleton } from '@/components/ui';
import { useCategories } from '@/lib/use-categories';

type Category = Schema<'CategoryResponse'>;

/** 카테고리 이름 최대 길이 (CategoryRequest) */
const NAME_MAX_LENGTH = 20;

/**
 * 카테고리 관리 (관리자): 대분류·소분류 추가, 이름 바꾸기, 삭제.
 * 상품은 소분류에만 등록할 수 있다. 하위 카테고리나 상품이 있는 카테고리는 서버가 삭제를 막는다.
 */
export function CategoryAdminPage() {
  const session = useSession();
  const categories = useCategories();

  if (session?.user.role !== 'ADMIN') {
    return <EmptyState title="관리자만 쓸 수 있는 화면이에요" description="카테고리는 관리자가 관리해요." />;
  }

  return (
    <div className="mx-auto max-w-[880px]">
      <PageHeader title="카테고리 관리" description="대분류 아래에 소분류를 만들어요. 상품은 소분류에만 등록할 수 있어요." />
      <Card className="mb-6">
        <CardBody className="pt-5">
          <AddForm label="대분류 추가" placeholder="예) 카메라" />
        </CardBody>
      </Card>

      {categories.isPending ? (
        <div className="flex flex-col gap-3">
          <Skeleton className="h-32 rounded-lg" />
          <Skeleton className="h-32 rounded-lg" />
        </div>
      ) : categories.isError ? (
        <ErrorState message={errorMessage(categories.error)} onRetry={() => void categories.refetch()} />
      ) : (categories.data ?? []).length === 0 ? (
        <EmptyState title="카테고리가 없어요" description="대분류를 먼저 추가하고, 그 아래에 소분류를 추가하세요." />
      ) : (
        <ul className="flex flex-col gap-4">
          {(categories.data ?? []).map((parent) => (
            <li key={parent.id}>
              <Card>
                <CardBody className="flex flex-col gap-3 pt-4">
                  <CategoryRow category={parent} level="대분류" />
                  <ul className="flex flex-col gap-1 border-l-2 border-neutral-100 pl-4">
                    {(parent.children ?? []).map((child) => (
                      <li key={child.id}>
                        <CategoryRow category={child} level="소분류" />
                      </li>
                    ))}
                    {(parent.children ?? []).length === 0 && <li className="text-label text-neutral-500">소분류가 없어요. 소분류를 추가해야 상품을 등록할 수 있어요.</li>}
                  </ul>
                  <AddForm label={`${parent.name ?? ''} 소분류 추가`} placeholder="예) 필름 카메라" parentId={parent.id} compact />
                </CardBody>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function useInvalidateCategories() {
  const queryClient = useQueryClient();
  return () => void queryClient.invalidateQueries({ queryKey: ['categories'] });
}

function AddForm({ label, placeholder, parentId, compact = false }: { label: string; placeholder: string; parentId?: number; compact?: boolean }) {
  const [name, setName] = useState('');
  const [error, setError] = useState<string>();
  const invalidate = useInvalidateCategories();
  const mutation = useMutation({
    mutationFn: () => call(api.POST('/api/v1/admin/categories', { body: { parentId, name: name.trim() } })),
    onSuccess: () => {
      setName('');
      invalidate();
    },
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setError('이름을 입력해 주세요.');
      return;
    }
    mutation.mutate();
  };

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-2">
      <div className="flex items-end gap-2">
        <div className="flex-1">
          <Input
            label={label}
            hideLabel={compact}
            placeholder={placeholder}
            maxLength={NAME_MAX_LENGTH}
            value={name}
            onChange={(e) => {
              setName(e.target.value);
              setError(undefined);
            }}
            error={error}
          />
        </div>
        <Button type="submit" variant={compact ? 'outline' : 'secondary'} loading={mutation.isPending}>
          <Plus className="size-4" aria-hidden="true" />
          {compact ? '소분류 추가' : '추가'}
        </Button>
      </div>
      {mutation.isError && <Notice tone="error">{errorMessage(mutation.error)}</Notice>}
    </form>
  );
}

function CategoryRow({ category, level }: { category: Category; level: '대분류' | '소분류' }) {
  const categoryId = category.id ?? 0;
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(category.name ?? '');
  const [confirmDelete, setConfirmDelete] = useState(false);
  const invalidate = useInvalidateCategories();

  // 명세의 요청 타입 이름이 상품 수정과 겹쳐(UpdateRequest) 상품 필드로 생성돼 있다. 두 타입에 모두 있는 name만 보낸다.
  const rename = useMutation({
    mutationFn: () => call(api.PATCH('/api/v1/admin/categories/{categoryId}', { params: { path: { categoryId } }, body: { name: name.trim() } })),
    onSuccess: () => {
      setEditing(false);
      invalidate();
    },
  });
  const remove = useMutation({
    mutationFn: () => call(api.DELETE('/api/v1/admin/categories/{categoryId}', { params: { path: { categoryId } } })),
    onSuccess: () => {
      setConfirmDelete(false);
      invalidate();
    },
  });

  if (editing) {
    return (
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (name.trim()) rename.mutate();
        }}
        className="flex flex-col gap-2"
      >
        <div className="flex items-end gap-2">
          <div className="flex-1">
            <Input label={`${level} 이름`} hideLabel maxLength={NAME_MAX_LENGTH} value={name} onChange={(e) => setName(e.target.value)} autoFocus />
          </div>
          <Button type="submit" variant="secondary" size="sm" loading={rename.isPending} disabled={!name.trim()}>
            저장
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => {
              setEditing(false);
              setName(category.name ?? '');
              rename.reset();
            }}
          >
            취소
          </Button>
        </div>
        {rename.isError && <Notice tone="error">{errorMessage(rename.error)}</Notice>}
      </form>
    );
  }

  return (
    <div className="flex items-center justify-between gap-2">
      <span className={level === '대분류' ? 'text-body-lg font-bold' : 'text-body'}>{category.name}</span>
      <div className="flex gap-1">
        <Button variant="ghost" size="sm" aria-label={`${category.name} 이름 바꾸기`} onClick={() => setEditing(true)}>
          <Pencil className="size-4" aria-hidden="true" />
        </Button>
        <Button variant="ghost" size="sm" aria-label={`${category.name} 삭제`} onClick={() => setConfirmDelete(true)}>
          <Trash2 className="size-4" aria-hidden="true" />
        </Button>
      </div>
      <ConfirmDialog
        open={confirmDelete}
        title={`‘${category.name}’ ${level}를 삭제할까요?`}
        description={
          <div className="flex flex-col gap-2">
            <p>하위 카테고리나 등록된 상품이 있으면 삭제할 수 없어요.</p>
            {remove.isError && <Notice tone="error">{errorMessage(remove.error)}</Notice>}
          </div>
        }
        confirmLabel="삭제"
        loading={remove.isPending}
        onConfirm={() => remove.mutate()}
        onCancel={() => {
          setConfirmDelete(false);
          remove.reset();
        }}
      />
    </div>
  );
}
