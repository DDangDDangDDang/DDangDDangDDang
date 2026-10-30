import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { api, call, errorMessage, isErrorCode, type Schema } from '@/api';
import { PageHeader } from '@/components/layout/PageHeader';
import { Button, buttonVariants, Card, CardBody, EmptyState, ErrorState, Notice, Skeleton } from '@/components/ui';
import { useCategories } from '@/lib/use-categories';
import type { ProductNoticeState } from './ProductCreatePage';
import { ProductFields } from '@/components/product/ProductFields';
import { useProductImageUploading } from '@/components/product/use-product-image-upload';
import { applyProductChange, productFormValuesOf, toProductRequest, validateProductForm, type ProductFormErrors, type ProductFormValues } from '@/lib/product-form';

/** 상품 수정. 시작 전이거나 진행 중인 경매가 있는 상품은 서버가 막는다. (PRODUCT_004) */
export function ProductEditPage() {
  const productId = Number(useParams().productId);
  const categories = useCategories();
  const query = useQuery({
    queryKey: ['product', productId],
    queryFn: () => call(api.GET('/api/v1/products/{productId}', { params: { path: { productId } } })),
    enabled: Number.isInteger(productId) && productId > 0,
  });

  if (!Number.isInteger(productId) || productId <= 0) return <NotFound />;
  if (query.isPending || categories.isPending) return <Skeleton className="mx-auto h-96 max-w-[880px] rounded-lg" />;
  if (query.isError) {
    if (isErrorCode(query.error, 'PRODUCT_001')) return <NotFound />;
    return <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />;
  }
  if (categories.isError) return <ErrorState message={errorMessage(categories.error)} onRetry={() => void categories.refetch()} />;
  if (!query.data || query.data.status === 'DELETED') return <NotFound />;

  return <EditForm product={query.data} productId={productId} categories={categories.data ?? []} />;
}

function EditForm({ product, productId, categories }: { product: Schema<'ProductResponse'>; productId: number; categories: Schema<'CategoryResponse'>[] }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [values, setValues] = useState<ProductFormValues>(() => productFormValuesOf(product));
  const [errors, setErrors] = useState<ProductFormErrors>({});
  const uploading = useProductImageUploading();

  const mutation = useMutation({
    mutationFn: () => call(api.PATCH('/api/v1/products/{productId}', { params: { path: { productId } }, body: toProductRequest(values) })),
    onSuccess: (saved) => {
      queryClient.setQueryData(['product', productId], saved);
      void queryClient.invalidateQueries({ queryKey: ['products', 'mine'] });
      navigate('/me/products', { state: { notice: `‘${saved?.name ?? values.name.trim()}’ 상품을 수정했어요.` } satisfies ProductNoticeState });
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
    <div className="mx-auto max-w-[880px]">
      <PageHeader title="상품 수정" />
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-6">
        <Card>
          <CardBody className="pt-5">
            <ProductFields
              categories={categories}
              values={values}
              errors={errors}
              onChange={(change) => {
                setValues((prev) => applyProductChange(prev, change));
                setErrors({});
              }}
            />
          </CardBody>
        </Card>
        {mutation.isError && <Notice tone="error">{errorMessage(mutation.error)}</Notice>}
        <div className="flex justify-end gap-2">
          <Link to="/me/products" className={buttonVariants({ variant: 'outline', size: 'lg' })}>
            취소
          </Link>
          <Button type="submit" size="lg" variant="secondary" loading={mutation.isPending} disabled={uploading}>
            {uploading ? '이미지 올리는 중' : '저장'}
          </Button>
        </div>
      </form>
    </div>
  );
}

function NotFound() {
  return (
    <EmptyState
      title="상품을 찾을 수 없어요"
      description="삭제됐거나 없는 상품이에요."
      action={
        <Link to="/me/products" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
          내 상품으로
        </Link>
      }
    />
  );
}
