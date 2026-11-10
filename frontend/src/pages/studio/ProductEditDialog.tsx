import { useIsMutating, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api, call, errorMessage, type Schema } from '@/api';
import { ProductFields } from '@/components/product/ProductFields';
import { useProductImageUploading } from '@/components/product/use-product-image-upload';
import { Button, Dialog, ErrorState, Notice, Skeleton } from '@/components/ui';
import { applyProductChange, productFormValuesOf, toProductRequest, validateProductForm, type ProductFormErrors, type ProductFormValues } from '@/lib/product-form';
import { useCategories } from '@/lib/use-categories';

/** 상품 수정 요청. 창이 요청 중인지 알 수 있게 이름을 붙인다. */
const EDIT_PRODUCT_KEY = ['studio', 'edit-product'];

type Props = {
  /** 고칠 상품 번호. 없으면 창을 닫는다. */
  productId?: number;
  onClose: () => void;
};

/**
 * 방송 중에도 스튜디오를 떠나지 않고 상품을 고치는 창. 내 상품의 상품 수정 화면과 같은 폼을 쓴다.
 * 시작 전·진행 중 경매가 있는 상품은 서버가 막는다. (PRODUCT_004) 삭제는 방송 중 실수를 막으려고 여기 두지 않는다.
 */
export function ProductEditDialog({ productId, onClose }: Props) {
  // 저장 중에는 닫지 못하게 한다. (닫았다 다시 열면 이전 요청의 결과가 새 창에 섞임)
  const saving = useIsMutating({ mutationKey: EDIT_PRODUCT_KEY }) > 0;
  return (
    <Dialog open={productId !== undefined} onClose={onClose} closeDisabled={saving} title="상품 수정" description="저장하면 경매 상품 목록에 바로 반영돼요.">
      {productId !== undefined && <EditLoader key={productId} productId={productId} onClose={onClose} />}
    </Dialog>
  );
}

function EditLoader({ productId, onClose }: { productId: number; onClose: () => void }) {
  const categories = useCategories();
  // 목록 응답에는 설명·이미지가 다 들어 있지 않으므로 상품 하나를 새로 불러온다.
  const product = useQuery({
    queryKey: ['product', productId],
    queryFn: () => call(api.GET('/api/v1/products/{productId}', { params: { path: { productId } } })),
  });

  if (product.isPending || categories.isPending) return <Skeleton className="h-72" />;
  if (product.isError) return <ErrorState message={errorMessage(product.error)} onRetry={() => void product.refetch()} />;
  if (categories.isError) return <ErrorState message={errorMessage(categories.error)} onRetry={() => void categories.refetch()} />;
  return <EditForm product={product.data} productId={productId} categories={categories.data ?? []} onClose={onClose} />;
}

function EditForm({
  product,
  productId,
  categories,
  onClose,
}: {
  product: Schema<'ProductResponse'>;
  productId: number;
  categories: Schema<'CategoryResponse'>[];
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [values, setValues] = useState<ProductFormValues>(() => productFormValuesOf(product));
  const [errors, setErrors] = useState<ProductFormErrors>({});
  const uploading = useProductImageUploading();

  const mutation = useMutation({
    mutationKey: EDIT_PRODUCT_KEY,
    mutationFn: () => call(api.PATCH('/api/v1/products/{productId}', { params: { path: { productId } }, body: toProductRequest(values) })),
    onSuccess: async (saved) => {
      if (saved) queryClient.setQueryData(['product', productId], saved);
      // 스튜디오의 경매 상품 목록(과 내 상품 목록)을 새로 불러온 뒤 창을 닫는다.
      await queryClient.invalidateQueries({ queryKey: ['products', 'mine'] });
      onClose();
    },
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    // 올리는 중인 이미지는 아직 values에 없어 상품에 붙지 않으므로 끝날 때까지 저장하지 않는다.
    if (uploading) return;
    const next = validateProductForm(values);
    setErrors(next);
    if (Object.keys(next).length === 0) mutation.mutate();
  };

  return (
    <form onSubmit={onSubmit} noValidate aria-label="상품 수정" className="flex flex-col gap-4">
      <ProductFields
        categories={categories}
        values={values}
        errors={errors}
        onChange={(change) => {
          setValues((prev) => applyProductChange(prev, change));
          // 이미지 업로드가 끝나 붙는 변경(함수)은 이미지·설명 오류만 지운다.
          const keys = typeof change === 'function' ? ['images', 'description'] : Object.keys(change);
          setErrors((prev) => ({ ...prev, ...Object.fromEntries(keys.map((k) => [k, undefined])) }));
        }}
      />
      {mutation.isError && <Notice tone="error">{errorMessage(mutation.error)}</Notice>}
      <div className="flex justify-end gap-2">
        <Button type="button" variant="outline" onClick={onClose} disabled={mutation.isPending}>
          취소
        </Button>
        <Button type="submit" loading={mutation.isPending} disabled={uploading}>
          {uploading ? '이미지 올리는 중' : '저장'}
        </Button>
      </div>
    </form>
  );
}
