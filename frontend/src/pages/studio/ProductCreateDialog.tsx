import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api, call, errorMessage } from '@/api';
import { ProductFields } from '@/components/product/ProductFields';
import { Button, Dialog, EmptyState, ErrorState, Notice, Skeleton } from '@/components/ui';
import { productFormValuesOf, toProductRequest, validateProductForm, type ProductFormErrors, type ProductFormValues } from '@/lib/product-form';
import { useCategories } from '@/lib/use-categories';

type Props = {
  open: boolean;
  onClose: () => void;
  /** 등록한 상품 번호. 스튜디오는 이 상품의 경매 조건 입력을 바로 연다. */
  onCreated: (productId: number) => void;
  /** 방송 중이면 등록 뒤 바로 경매를 시작할 수 있다고 안내한다. */
  live: boolean;
};

/** 방송 중에도 스튜디오를 떠나지 않고 상품을 등록하는 창. 경매 조건은 등록 뒤 "경매 상품"에서 정한다. */
export function ProductCreateDialog({ open, onClose, onCreated, live }: Props) {
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="상품 등록"
      description={live ? '등록하면 바로 경매 조건을 정해 이 방송에서 경매를 시작할 수 있어요.' : '등록한 상품은 방송을 시작한 뒤 경매에 올릴 수 있어요.'}
    >
      <CreateForm onClose={onClose} onCreated={onCreated} />
    </Dialog>
  );
}

function CreateForm({ onClose, onCreated }: Pick<Props, 'onClose' | 'onCreated'>) {
  const queryClient = useQueryClient();
  const categories = useCategories();
  const [values, setValues] = useState<ProductFormValues>(() => productFormValuesOf());
  const [errors, setErrors] = useState<ProductFormErrors>({});

  const mutation = useMutation({
    mutationFn: () => call(api.POST('/api/v1/products', { body: toProductRequest(values) })),
    onSuccess: async (product) => {
      // 스튜디오의 상품 목록을 새로 불러온 뒤 그 상품의 경매 조건 입력을 연다.
      await queryClient.invalidateQueries({ queryKey: ['products', 'mine'] });
      if (product?.id) onCreated(product.id);
      onClose();
    },
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    const next = validateProductForm(values);
    setErrors(next);
    if (Object.keys(next).length === 0) mutation.mutate();
  };

  if (categories.isPending) return <Skeleton className="h-72" />;
  if (categories.isError) return <ErrorState message={errorMessage(categories.error)} onRetry={() => void categories.refetch()} />;
  if ((categories.data ?? []).every((c) => (c.children ?? []).length === 0)) {
    return <EmptyState title="아직 상품을 등록할 카테고리가 없어요" description="관리자가 카테고리(대분류 › 소분류)를 만들면 상품을 등록할 수 있어요." />;
  }

  return (
    <form onSubmit={onSubmit} noValidate aria-label="상품 등록" className="flex flex-col gap-4">
      <ProductFields
        categories={categories.data ?? []}
        values={values}
        errors={errors}
        onChange={(patch) => {
          setValues((prev) => ({ ...prev, ...patch }));
          setErrors((prev) => ({ ...prev, ...Object.fromEntries(Object.keys(patch).map((k) => [k, undefined])) }));
        }}
      />
      {mutation.isError && <Notice tone="error">{errorMessage(mutation.error)}</Notice>}
      <div className="flex justify-end gap-2">
        <Button type="button" variant="outline" onClick={onClose} disabled={mutation.isPending}>
          취소
        </Button>
        <Button type="submit" loading={mutation.isPending}>
          상품 등록
        </Button>
      </div>
    </form>
  );
}
