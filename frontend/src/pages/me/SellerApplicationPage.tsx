import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ShieldCheck } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { api, call, errorMessage, isErrorCode, useSession, type Schema } from '@/api';
import { PageHeader } from '@/components/layout/PageHeader';
import { useOpenSellerProfile } from '@/components/seller/seller-profile-context';
import { Badge, Button, Card, CardBody, CardHeader, CardTitle, ErrorState, Input, Notice, Skeleton, Textarea } from '@/components/ui';
import { formatDateTime } from '@/lib/format';
import { SELLER_APPLICATION_STATUS_LABEL } from '@/lib/labels';
import { sellerProfileKey } from '@/lib/use-seller';

type Application = Schema<'SellerApplicationResponse'>;

const MY_APPLICATION_KEY = ['seller-application', 'mine'] as const;
/** 서버와 같은 길이 제한 (SellerApplicationRequest) */
const STORE_NAME_MAX = 30;
const INTRODUCTION_MAX = 500;

/**
 * 판매자 인증 신청: 상점 이름과 소개를 적어 신청하면 관리자가 심사한다.
 * 승인되면 프로필 썸네일에 인증 마크가 붙고 판매자 프로필 창이 열린다. 반려되면 사유를 보고 고쳐 다시 신청한다.
 */
export function SellerApplicationPage() {
  const query = useQuery({
    queryKey: MY_APPLICATION_KEY,
    queryFn: () => call(api.GET('/api/v1/seller-applications/me')),
  });

  return (
    <div className="mx-auto flex max-w-[720px] flex-col gap-4">
      <PageHeader title="판매자 인증" description="인증을 받으면 시청자에게 판매자 인증 마크와 판매자 프로필이 보여요." />
      {query.isPending ? (
        <Skeleton className="h-64 rounded-lg" />
      ) : query.isError ? (
        <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
      ) : (
        <ApplicationCard application={query.data ?? undefined} />
      )}
    </div>
  );
}

function ApplicationCard({ application }: { application?: Application }) {
  const session = useSession();
  const openProfile = useOpenSellerProfile();
  const status = application?.status;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <ShieldCheck className="size-5 text-info-600" aria-hidden="true" />
          {status ? '내 신청' : '판매자 인증 신청'}
        </CardTitle>
        {status && <Badge variant={status === 'APPROVED' ? 'info' : status === 'REJECTED' ? 'warning' : 'outline'}>{SELLER_APPLICATION_STATUS_LABEL[status]}</Badge>}
      </CardHeader>
      <CardBody className="flex flex-col gap-4">
        {status === 'PENDING' && <Notice>관리자가 신청을 확인하고 있어요. 결과가 나오면 이 화면에 표시돼요.</Notice>}
        {status === 'APPROVED' && (
          <div className="flex items-center justify-between gap-4">
            <Notice tone="success">{application?.reviewedAt ? `${formatDateTime(application.reviewedAt)}에 ` : ''}판매자 인증을 받았어요.</Notice>
            {session?.user.userId !== undefined && (
              <Button variant="outline" size="sm" onClick={() => openProfile(session.user.userId ?? 0)}>
                내 판매자 프로필 보기
              </Button>
            )}
          </div>
        )}
        {status === 'REJECTED' && <Notice tone="error">반려됐어요. 사유: {application?.rejectReason || '-'} 내용을 고쳐 다시 신청할 수 있어요.</Notice>}

        {status === 'PENDING' || status === 'APPROVED' ? (
          <dl className="grid grid-cols-[100px_1fr] gap-y-3 text-body">
            <dt className="text-neutral-500">상점 이름</dt>
            <dd>{application?.storeName}</dd>
            <dt className="text-neutral-500">소개</dt>
            <dd className="whitespace-pre-line">{application?.introduction}</dd>
            <dt className="text-neutral-500">신청일</dt>
            <dd>{application?.createdAt ? formatDateTime(application.createdAt) : '-'}</dd>
          </dl>
        ) : (
          <ApplicationForm previous={application} />
        )}
      </CardBody>
    </Card>
  );
}

function ApplicationForm({ previous }: { previous?: Application }) {
  const queryClient = useQueryClient();
  const session = useSession();
  const [storeName, setStoreName] = useState(previous?.storeName ?? '');
  const [introduction, setIntroduction] = useState(previous?.introduction ?? '');
  const [errors, setErrors] = useState<{ storeName?: string; introduction?: string }>({});

  const apply = useMutation({
    mutationFn: () => call(api.POST('/api/v1/seller-applications', { body: { storeName: storeName.trim(), introduction: introduction.trim() } })),
    onSuccess: (saved) => {
      queryClient.setQueryData(MY_APPLICATION_KEY, saved);
      if (session?.user.userId !== undefined) void queryClient.invalidateQueries({ queryKey: sellerProfileKey(session.user.userId) });
    },
    onError: (error) => {
      // 다른 탭에서 이미 신청했거나 인증을 받았으면 지금 상태를 다시 불러온다.
      if (isErrorCode(error, 'SELLER_001') || isErrorCode(error, 'SELLER_002')) void queryClient.invalidateQueries({ queryKey: MY_APPLICATION_KEY });
    },
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    const next: typeof errors = {};
    if (!storeName.trim()) next.storeName = '상점 이름을 입력해 주세요.';
    if (!introduction.trim()) next.introduction = '어떤 상품을 파는지 소개해 주세요.';
    setErrors(next);
    if (Object.keys(next).length === 0) apply.mutate();
  };

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      <Input
        label={`상점 이름 (${STORE_NAME_MAX}자 이하)`}
        placeholder="예) 필름 카메라 상점"
        maxLength={STORE_NAME_MAX}
        value={storeName}
        onChange={(e) => {
          setStoreName(e.target.value);
          setErrors((prev) => ({ ...prev, storeName: undefined }));
        }}
        error={errors.storeName}
        required
      />
      <Textarea
        label="판매자 소개"
        placeholder="어떤 상품을 어떻게 관리해서 파는지 시청자에게 알려 주세요."
        maxLength={INTRODUCTION_MAX}
        rows={4}
        value={introduction}
        onChange={(e) => {
          setIntroduction(e.target.value);
          setErrors((prev) => ({ ...prev, introduction: undefined }));
        }}
        error={errors.introduction}
        hint={
          <span>
            연락처나 외부 링크는 적을 수 없어요. <span className="num">{introduction.length}</span>/{INTRODUCTION_MAX}
          </span>
        }
      />
      {apply.isError && <Notice tone="error">{errorMessage(apply.error)}</Notice>}
      <Button type="submit" className="self-start" loading={apply.isPending}>
        {previous ? '다시 신청하기' : '신청하기'}
      </Button>
    </form>
  );
}
