import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { api, call, errorMessage, isErrorCode } from '@/api';
import { AuctionConditionFields } from '@/components/auction/AuctionConditionFields';
import { PageHeader } from '@/components/layout/PageHeader';
import { Button, buttonVariants, Card, CardBody, EmptyState, ErrorState, Notice, Skeleton } from '@/components/ui';
import { EMPTY_AUCTION_CONDITION, toAuctionCreateRequest, validateAuctionCondition, type AuctionConditionErrors, type AuctionConditionValues } from '@/lib/auction';
import { PRODUCT_STATUS_LABEL } from '@/lib/labels';
import type { ProductNoticeState } from './ProductCreatePage';

/** 등록한 상품의 경매 일정 잡기 (시작·마감 시각). 방송 중 경매는 판매자 스튜디오에서 시작한다. */
export function ProductAuctionPage() {
  const productId = Number(useParams().productId);
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const passedError = (location.state as ProductNoticeState)?.error;
  const [values, setValues] = useState<AuctionConditionValues>(EMPTY_AUCTION_CONDITION);
  const [errors, setErrors] = useState<AuctionConditionErrors>({});

  const valid = Number.isInteger(productId) && productId > 0;
  const query = useQuery({
    queryKey: ['product', productId],
    queryFn: () => call(api.GET('/api/v1/products/{productId}', { params: { path: { productId } } })),
    enabled: valid,
  });

  const mutation = useMutation({
    mutationFn: () => call(api.POST('/api/v1/auctions', { body: toAuctionCreateRequest(productId, values) })),
    onSuccess: (auction) => {
      void queryClient.invalidateQueries({ queryKey: ['auctions'] });
      navigate(`/auctions/${auction?.id ?? ''}`);
    },
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    const next = validateAuctionCondition(values, 'schedule', new Date());
    setErrors(next);
    if (Object.keys(next).length === 0) mutation.mutate();
  };

  if (!valid) return <NotFound />;
  if (query.isPending) return <Skeleton className="mx-auto h-72 max-w-[880px] rounded-lg" />;
  if (query.isError) {
    if (isErrorCode(query.error, 'PRODUCT_001')) return <NotFound />;
    return <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />;
  }
  const product = query.data;
  if (!product || product.status === 'DELETED') return <NotFound />;

  return (
    <div className="mx-auto max-w-[880px]">
      <PageHeader title="경매 일정 잡기" description={`${product.name ?? '상품'} · ${product.categoryName ?? ''}`} />
      {passedError && !mutation.isSuccess && <Notice tone="error" className="mb-4">{passedError}</Notice>}
      {product.status !== 'ACTIVE' ? (
        <EmptyState title={`${PRODUCT_STATUS_LABEL[product.status ?? 'SOLD']} 상품은 경매에 올릴 수 없어요`} description="판매 중인 상품만 경매에 올릴 수 있어요." />
      ) : (
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-6">
          <Card>
            <CardBody className="pt-5">
              <AuctionConditionFields
                mode="schedule"
                values={values}
                errors={errors}
                onChange={(patch) => {
                  setValues((prev) => ({ ...prev, ...patch }));
                  setErrors((prev) => ({ ...prev, ...Object.fromEntries(Object.keys(patch).map((k) => [k, undefined])) }));
                }}
              />
            </CardBody>
          </Card>
          {mutation.isError && <Notice tone="error">{errorMessage(mutation.error)}</Notice>}
          <div className="flex justify-end gap-2">
            <Link to="/me/products" className={buttonVariants({ variant: 'outline', size: 'lg' })}>
              취소
            </Link>
            <Button type="submit" size="lg" loading={mutation.isPending}>
              경매 열기
            </Button>
          </div>
        </form>
      )}
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
