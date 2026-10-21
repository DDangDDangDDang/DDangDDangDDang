import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router';
import { api, call, errorMessage, type Schema } from '@/api';
import { PageHeader } from '@/components/layout/PageHeader';
import { ProductImage } from '@/components/order/OrderBits';
import { Badge, buttonVariants, Button, Card, ConfirmDialog, EmptyState, ErrorState, Notice, Pagination, Skeleton } from '@/components/ui';
import { formatDate } from '@/lib/format';
import { PRODUCT_STATUS_LABEL } from '@/lib/labels';
import type { ProductNoticeState } from './ProductCreatePage';

const PAGE_SIZE = 10;
type Product = Schema<'ProductResponse'>;

/** 내 상품: 등록한 상품 목록(최신순) + 수정·삭제·경매 일정 잡기. 방송 중 경매는 판매자 스튜디오에서 시작한다. */
export function MyProductsPage() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get('page')) || 0);
  const location = useLocation();
  const notice = (location.state as ProductNoticeState)?.notice;
  const queryClient = useQueryClient();
  const [deleting, setDeleting] = useState<Product>();

  const query = useQuery({
    queryKey: ['products', 'mine', page],
    queryFn: () => call(api.GET('/api/v1/users/me/products', { params: { query: { page, size: PAGE_SIZE, sort: ['createdAt,desc'] } } })),
    placeholderData: keepPreviousData,
  });

  const remove = useMutation({
    mutationFn: (productId: number) => call(api.DELETE('/api/v1/products/{productId}', { params: { path: { productId } } })),
    onSuccess: () => {
      setDeleting(undefined);
      void queryClient.invalidateQueries({ queryKey: ['products', 'mine'] });
    },
  });

  // 삭제된 상품은 목록에서 뺀다. (서버는 상태만 DELETED로 바꾼다)
  const products = (query.data?.content ?? []).filter((p) => p.status !== 'DELETED');
  const totalPages = query.data?.totalPages ?? 0;

  // 마지막 페이지의 상품을 지웠거나 주소에 없는 페이지를 넣었으면 마지막 페이지로 옮긴다.
  useEffect(() => {
    if (query.isPlaceholderData || totalPages === 0 || page < totalPages) return;
    const last = totalPages - 1;
    setParams(last > 0 ? { page: String(last) } : {}, { replace: true });
  }, [page, totalPages, query.isPlaceholderData, setParams]);

  return (
    <div className="mx-auto max-w-[880px]">
      <PageHeader
        title="내 상품"
        description="등록한 상품으로 경매를 열 수 있어요. 방송 중 경매는 판매자 스튜디오에서 시작해요."
        actions={
          <Link to="/products/new" className={buttonVariants({ variant: 'primary', size: 'sm' })}>
            <Plus className="size-4" aria-hidden="true" />
            상품 등록
          </Link>
        }
      />
      {notice && (
        <Notice tone="success" className="mb-4">
          {notice}
        </Notice>
      )}

      <Card>
        {query.isPending ? (
          <div className="flex flex-col gap-3 p-5">
            {Array.from({ length: 4 }, (_, i) => (
              <Skeleton key={i} className="h-20" />
            ))}
          </div>
        ) : query.isError ? (
          <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
        ) : products.length === 0 ? (
          <EmptyState
            title="등록한 상품이 없어요"
            description="상품을 등록하면 경매 일정을 잡거나 방송 중에 바로 경매할 수 있어요."
            action={
              <Link to="/products/new" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
                상품 등록
              </Link>
            }
          />
        ) : (
          <ul className="divide-y divide-neutral-100">
            {products.map((product) => (
              <li key={product.id} className="flex items-center gap-4 px-5 py-4">
                <ProductImage alt={product.name ?? '상품'} className="size-16" />
                <div className="flex min-w-0 flex-1 flex-col gap-1">
                  <div className="flex items-center gap-2">
                    {product.status && <Badge variant={product.status === 'ACTIVE' ? 'info' : 'neutral'}>{PRODUCT_STATUS_LABEL[product.status]}</Badge>}
                    <span className="text-caption text-neutral-500">
                      {product.categoryName ?? ''}
                      {product.createdAt ? ` · ${formatDate(product.createdAt)} 등록` : ''}
                    </span>
                  </div>
                  <p className="truncate text-body-lg font-semibold">{product.name || '이름 없는 상품'}</p>
                </div>
                {product.status === 'ACTIVE' && (
                  <div className="flex gap-2">
                    <Link to={`/products/${product.id}/auction`} className={buttonVariants({ variant: 'secondary', size: 'sm' })}>
                      경매 일정 잡기
                    </Link>
                    <Link to={`/products/${product.id}/edit`} className={buttonVariants({ variant: 'outline', size: 'sm' })}>
                      수정
                    </Link>
                    <Button variant="ghost" size="sm" onClick={() => setDeleting(product)}>
                      삭제
                    </Button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Pagination page={page} totalPages={totalPages} onChange={(next) => setParams(next > 0 ? { page: String(next) } : {})} label="내 상품 페이지" />

      <ConfirmDialog
        open={deleting !== undefined}
        title="상품을 삭제할까요?"
        description={
          <div className="flex flex-col gap-2">
            <p>‘{deleting?.name}’ 상품을 삭제하면 되돌릴 수 없어요. 시작 전이거나 진행 중인 경매가 있으면 삭제할 수 없어요.</p>
            {remove.isError && <Notice tone="error">{errorMessage(remove.error)}</Notice>}
          </div>
        }
        confirmLabel="삭제"
        loading={remove.isPending}
        onConfirm={() => deleting?.id && remove.mutate(deleting.id)}
        onCancel={() => {
          setDeleting(undefined);
          remove.reset();
        }}
      />
    </div>
  );
}
