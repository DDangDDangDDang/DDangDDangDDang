import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router';
import { api, call, errorMessage } from '@/api';
import { AuctionConditionFields } from '@/components/auction/AuctionConditionFields';
import { PageHeader } from '@/components/layout/PageHeader';
import { Button, buttonVariants, Card, CardBody, EmptyState, ErrorState, Notice, Skeleton } from '@/components/ui';
import { cn } from '@/lib/cn';
import {
  EMPTY_AUCTION_CONDITION,
  toAuctionCreateRequest,
  validateAuctionCondition,
  type AuctionConditionErrors,
  type AuctionConditionValues,
} from '@/lib/auction';
import { useCategories } from '@/lib/use-categories';
import { ProductFields } from '@/components/product/ProductFields';
import { useProductImageUploading } from '@/components/product/use-product-image-upload';
import { applyProductChange, productFormValuesOf, toProductRequest, validateProductForm, type ProductFormErrors, type ProductFormValues } from '@/lib/product-form';

/** 등록 뒤 경매: later는 상품만 등록(방송 중에 스튜디오에서 시작), schedule은 시작·마감 시각을 정해 바로 경매 등록 */
type AuctionChoice = 'later' | 'schedule';

/** 넘겨받은 안내 (경매 등록 화면·내 상품으로 이동할 때) */
export type ProductNoticeState = { notice?: string; error?: string } | null;

/** 경매 상품 등록: 상품 정보 + (선택) 경매 일정. 방송 중 경매는 판매자 스튜디오에서 시작한다. */
export function ProductCreatePage() {
  const categories = useCategories();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [values, setValues] = useState<ProductFormValues>(() => productFormValuesOf());
  const [errors, setErrors] = useState<ProductFormErrors>({});
  const [choice, setChoice] = useState<AuctionChoice>('later');
  const [condition, setCondition] = useState<AuctionConditionValues>(EMPTY_AUCTION_CONDITION);
  const [conditionErrors, setConditionErrors] = useState<AuctionConditionErrors>({});
  const uploading = useProductImageUploading();

  const mutation = useMutation({
    mutationFn: async () => {
      const product = await call(api.POST('/api/v1/products', { body: toProductRequest(values) }));
      const productId = product?.id ?? 0;
      if (choice === 'later') return { productId };
      try {
        const auction = await call(api.POST('/api/v1/auctions', { body: toAuctionCreateRequest(productId, condition) }));
        return { productId, auctionId: auction?.id };
      } catch (error) {
        // 상품은 등록됐다. 경매 등록 화면에서 다시 시도하게 한다.
        return { productId, auctionError: errorMessage(error) };
      }
    },
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: ['products', 'mine'] });
      if (result.auctionId) {
        void queryClient.invalidateQueries({ queryKey: ['auctions'] });
        navigate(`/auctions/${result.auctionId}`);
      } else if (result.auctionError) {
        navigate(`/products/${result.productId}/auction`, { state: { error: `상품은 등록했지만 경매는 등록하지 못했어요. ${result.auctionError}` } satisfies ProductNoticeState });
      } else {
        navigate('/me/products', { state: { notice: `‘${values.name.trim()}’ 상품을 등록했어요. 방송 중에 판매자 스튜디오에서 경매를 시작할 수 있어요.` } satisfies ProductNoticeState });
      }
    },
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    // 올리는 중인 이미지는 아직 values에 없어 상품에 붙지 않으므로 끝날 때까지 등록하지 않는다.
    if (uploading) return;
    const nextErrors = validateProductForm(values);
    const nextConditionErrors = choice === 'schedule' ? validateAuctionCondition(condition, 'schedule', new Date()) : {};
    setErrors(nextErrors);
    setConditionErrors(nextConditionErrors);
    if (Object.keys(nextErrors).length === 0 && Object.keys(nextConditionErrors).length === 0) mutation.mutate();
  };

  return (
    <div className="mx-auto max-w-[880px]">
      <PageHeader title="경매 상품 등록" description="등록한 상품은 바로 경매 일정을 잡거나, 방송 중에 판매자 스튜디오에서 경매를 시작할 수 있어요." />

      {categories.isPending ? (
        <Skeleton className="h-96 rounded-lg" />
      ) : categories.isError ? (
        <ErrorState message={errorMessage(categories.error)} onRetry={() => void categories.refetch()} />
      ) : (categories.data ?? []).every((c) => (c.children ?? []).length === 0) ? (
        <EmptyState title="아직 상품을 등록할 카테고리가 없어요" description="관리자가 카테고리(대분류 › 소분류)를 만들면 상품을 등록할 수 있어요." />
      ) : (
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-6">
          <Section title="상품 정보">
            <ProductFields
              categories={categories.data ?? []}
              values={values}
              errors={errors}
              onChange={(change) => {
                setValues((prev) => applyProductChange(prev, change));
                // 이미지 업로드가 끝나 붙는 변경(함수)은 이미지·설명 오류만 지운다.
                const keys = typeof change === 'function' ? ['images', 'description'] : Object.keys(change);
                setErrors((prev) => ({ ...prev, ...Object.fromEntries(keys.map((k) => [k, undefined])) }));
              }}
            />
          </Section>

          <Section title="경매">
            <fieldset className="flex flex-col gap-2">
              <legend className="sr-only">경매 방식</legend>
              <Choice checked={choice === 'later'} onSelect={() => setChoice('later')} title="상품만 등록" description="방송 중에 판매자 스튜디오에서 진행 시간(30초~3일)을 정해 바로 경매를 시작해요." />
              <Choice checked={choice === 'schedule'} onSelect={() => setChoice('schedule')} title="경매 일정 바로 잡기" description="시작·마감 시각을 정해 두면 그 시간에 경매가 열려요." />
            </fieldset>
            {choice === 'schedule' && (
              <div className="mt-4">
                <AuctionConditionFields
                  mode="schedule"
                  values={condition}
                  errors={conditionErrors}
                  onChange={(patch) => {
                    setCondition((prev) => ({ ...prev, ...patch }));
                    setConditionErrors((prev) => ({ ...prev, ...Object.fromEntries(Object.keys(patch).map((k) => [k, undefined])) }));
                  }}
                />
              </div>
            )}
          </Section>

          {mutation.isError && <Notice tone="error">{errorMessage(mutation.error)}</Notice>}

          <div className="flex justify-end gap-2">
            <Link to="/me/products" className={buttonVariants({ variant: 'outline', size: 'lg' })}>
              취소
            </Link>
            <Button type="submit" size="lg" loading={mutation.isPending} disabled={uploading}>
              {uploading ? '이미지 올리는 중' : choice === 'schedule' ? '상품 등록하고 경매 열기' : '상품 등록'}
            </Button>
          </div>
        </form>
      )}
    </div>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card>
      <CardBody className="pt-5">
        <h2 className="mb-4 text-title-md font-bold">{title}</h2>
        {children}
      </CardBody>
    </Card>
  );
}

function Choice({ checked, onSelect, title, description }: { checked: boolean; onSelect: () => void; title: string; description: string }) {
  return (
    <label className={cn('flex cursor-pointer items-start gap-3 rounded-md border px-4 py-3', checked ? 'border-neutral-900 bg-neutral-25' : 'border-neutral-200')}>
      <input type="radio" name="auction-choice" checked={checked} onChange={onSelect} className="mt-1 accent-neutral-900" />
      <span className="flex flex-col gap-0.5">
        <span className="text-body font-semibold">{title}</span>
        <span className="text-label text-neutral-500">{description}</span>
      </span>
    </label>
  );
}
