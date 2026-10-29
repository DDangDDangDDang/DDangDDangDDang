import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { api, call, errorMessage, type Schema } from '@/api';
import { Button, Card, CardBody, CardHeader, CardTitle, Input, Notice, Skeleton, Textarea, buttonVariants } from '@/components/ui';
import { sellerProfileKey } from '@/lib/use-seller';

type Application = Schema<'SellerApplicationResponse'>;

/** 판매자 인증 화면(SellerApplicationPage)과 같은 캐시 키 */
const MY_APPLICATION_KEY = ['seller-application', 'mine'] as const;
/** 서버와 같은 길이 제한 (SellerStoreUpdateRequest) */
const STORE_NAME_MAX = 30;
const INTRODUCTION_MAX = 500;

/**
 * 내 정보의 판매자 영역. 인증받은 판매자는 상점 이름·소개를 바로 고칠 수 있고(다시 심사받지 않음),
 * 아니면 판매자 인증 화면으로 안내한다.
 */
export function SellerStoreCard({ onNavigate }: { onNavigate?: () => void }) {
  const query = useQuery({
    queryKey: MY_APPLICATION_KEY,
    queryFn: () => call(api.GET('/api/v1/seller-applications/me')),
  });
  const application = query.data ?? undefined;

  if (application?.status === 'APPROVED') {
    return (
      <Card className="mt-4">
        <CardHeader>
          <CardTitle>상점 정보</CardTitle>
        </CardHeader>
        <CardBody>
          <StoreEditor application={application} />
        </CardBody>
      </Card>
    );
  }

  return (
    <Card className="mt-4">
      <CardHeader>
        <CardTitle>판매자 인증</CardTitle>
      </CardHeader>
      <CardBody className="flex items-center justify-between gap-4">
        {query.isPending ? (
          <Skeleton className="h-6 flex-1" />
        ) : (
          <p className="text-body text-neutral-500">
            인증을 받으면 내 프로필에 판매자 인증 마크가 붙고, 시청자가 판매자 프로필(방송·경매 기록, 거래 신뢰도)을 볼 수 있어요.
          </p>
        )}
        <Link to="/me/seller" onClick={onNavigate} className={buttonVariants({ variant: 'outline', className: 'shrink-0' })}>
          판매자 인증
        </Link>
      </CardBody>
    </Card>
  );
}

function StoreEditor({ application }: { application: Application }) {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [storeName, setStoreName] = useState('');
  const [introduction, setIntroduction] = useState('');

  const mutation = useMutation({
    mutationFn: () => call(api.PUT('/api/v1/seller-applications/me/store', { body: { storeName: storeName.trim(), introduction: introduction.trim() } })),
    onSuccess: (data) => {
      queryClient.setQueryData(MY_APPLICATION_KEY, data);
      if (application.userId !== undefined) void queryClient.invalidateQueries({ queryKey: sellerProfileKey(application.userId) });
      setEditing(false);
    },
  });

  if (!editing) {
    return (
      <div className="flex flex-col gap-3">
        <dl className="grid grid-cols-[120px_1fr] items-start gap-y-3 text-body">
          <dt className="text-neutral-500">상점 이름</dt>
          <dd className="font-semibold">{application.storeName}</dd>
          <dt className="text-neutral-500">판매자 소개</dt>
          <dd className="whitespace-pre-line">{application.introduction}</dd>
        </dl>
        <div className="flex items-center justify-between gap-4">
          {mutation.isSuccess ? <Notice tone="success">상점 정보를 저장했어요.</Notice> : <span />}
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              setStoreName(application.storeName ?? '');
              setIntroduction(application.introduction ?? '');
              mutation.reset();
              setEditing(true);
            }}
          >
            상점 정보 수정
          </Button>
        </div>
      </div>
    );
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate();
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-3">
      <Input label="상점 이름" value={storeName} maxLength={STORE_NAME_MAX} onChange={(e) => setStoreName(e.target.value)} />
      <Textarea
        label="판매자 소개"
        value={introduction}
        maxLength={INTRODUCTION_MAX}
        hint={`${introduction.length}/${INTRODUCTION_MAX}자 · 다시 심사받지 않고 바로 바뀌어요.`}
        onChange={(e) => setIntroduction(e.target.value)}
      />
      {mutation.isError && <Notice tone="error">{errorMessage(mutation.error)}</Notice>}
      <div className="flex justify-end gap-2">
        <Button variant="ghost" onClick={() => setEditing(false)} disabled={mutation.isPending}>
          취소
        </Button>
        <Button type="submit" loading={mutation.isPending} disabled={!storeName.trim() || !introduction.trim()}>
          저장
        </Button>
      </div>
    </form>
  );
}
