import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ShieldCheck } from "lucide-react";
import { useState, type FormEvent } from "react";
import {
  api,
  call,
  errorMessage,
  isErrorCode,
  useSession,
  type Schema,
} from "@/api";
import { PageHeader } from "@/components/layout/PageHeader";
import { useOpenSellerProfile } from "@/components/seller/seller-profile-context";
import {
  Badge,
  Button,
  Card,
  CardBody,
  CardHeader,
  CardTitle,
  ErrorState,
  Input,
  Notice,
  Skeleton,
  Textarea,
} from "@/components/ui";
import { formatDateTime } from "@/lib/format";
import { SELLER_APPLICATION_STATUS_LABEL } from "@/lib/labels";
import {
  businessNumberDigits,
  formatBusinessNumber,
  isValidBusinessNumber,
} from "@/lib/seller";
import { sellerProfileKey } from "@/lib/use-seller";

type Application = Schema<"SellerApplicationResponse">;

const MY_APPLICATION_KEY = ["seller-application", "mine"] as const;
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
    queryFn: () => call(api.GET("/api/v1/seller-applications/me")),
  });

  return (
    <div className="mx-auto flex max-w-[720px] flex-col gap-4">
      <PageHeader
        title="판매자 인증"
        description="인증을 받으면 시청자에게 판매자 인증 마크와 판매자 프로필이 보여요."
      />
      {query.isPending ? (
        <Skeleton className="h-64 rounded-lg" />
      ) : query.isError ? (
        <ErrorState
          message={errorMessage(query.error)}
          onRetry={() => void query.refetch()}
        />
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
          {status ? "내 신청" : "판매자 인증 신청"}
        </CardTitle>
        {status && (
          <Badge
            variant={
              status === "APPROVED"
                ? "info"
                : status === "REJECTED"
                  ? "warning"
                  : "outline"
            }
          >
            {SELLER_APPLICATION_STATUS_LABEL[status]}
          </Badge>
        )}
      </CardHeader>
      <CardBody className="flex flex-col gap-4">
        {status === "PENDING" && (
          <Notice>
            관리자가 신청을 확인하고 있어요. 결과가 나오면 이 화면에 표시돼요.
          </Notice>
        )}
        {status === "APPROVED" && (
          <div className="flex items-center justify-between gap-4">
            <Notice tone="success">
              {application?.reviewedAt
                ? `${formatDateTime(application.reviewedAt)}에 `
                : ""}
              판매자 인증을 받았어요.
            </Notice>
            {session?.user.userId !== undefined && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => openProfile(session.user.userId ?? 0)}
              >
                내 판매자 프로필 보기
              </Button>
            )}
          </div>
        )}
        {status === "REJECTED" && (
          <Notice tone="error">
            반려됐어요. 사유: {application?.rejectReason || "-"} 내용을 고쳐
            다시 신청할 수 있어요.
          </Notice>
        )}

        {status === "PENDING" || status === "APPROVED" ? (
          <dl className="grid grid-cols-[100px_1fr] gap-y-3 text-body">
            <dt className="text-neutral-500">상점 이름</dt>
            <dd>{application?.storeName}</dd>
            <dt className="text-neutral-500">소개</dt>
            <dd className="whitespace-pre-line">{application?.introduction}</dd>
            <dt className="text-neutral-500">판매자 유형</dt>
            <dd>
              {application?.businessNumber
                ? `사업자 (${formatBusinessNumber(application.businessNumber)})`
                : "개인"}
            </dd>
            <dt className="text-neutral-500">신청일</dt>
            <dd>
              {application?.createdAt
                ? formatDateTime(application.createdAt)
                : "-"}
            </dd>
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
  const [storeName, setStoreName] = useState(previous?.storeName ?? "");
  const [introduction, setIntroduction] = useState(
    previous?.introduction ?? "",
  );
  // 사업자 판매자면 사업자등록번호를 함께 낸다. 승인되면 프로필에 사업자 배지가 붙는다.
  const [business, setBusiness] = useState(Boolean(previous?.businessNumber));
  const [businessNumber, setBusinessNumber] = useState(
    previous?.businessNumber
      ? formatBusinessNumber(previous.businessNumber)
      : "",
  );
  const [errors, setErrors] = useState<{
    storeName?: string;
    introduction?: string;
    businessNumber?: string;
  }>({});

  const apply = useMutation({
    mutationFn: () =>
      call(
        api.POST("/api/v1/seller-applications", {
          body: {
            storeName: storeName.trim(),
            introduction: introduction.trim(),
            businessNumber: business
              ? businessNumberDigits(businessNumber)
              : undefined,
          },
        }),
      ),
    onSuccess: (saved) => {
      queryClient.setQueryData(MY_APPLICATION_KEY, saved);
      if (session?.user.userId !== undefined)
        void queryClient.invalidateQueries({
          queryKey: sellerProfileKey(session.user.userId),
        });
    },
    onError: (error) => {
      // 다른 탭에서 이미 신청했거나 인증을 받았으면 지금 상태를 다시 불러온다.
      if (isErrorCode(error, "SELLER_001") || isErrorCode(error, "SELLER_002"))
        void queryClient.invalidateQueries({ queryKey: MY_APPLICATION_KEY });
      if (isErrorCode(error, "SELLER_007"))
        setErrors((prev) => ({ ...prev, businessNumber: errorMessage(error) }));
    },
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    const next: typeof errors = {};
    if (!storeName.trim()) next.storeName = "상점 이름을 입력해 주세요.";
    if (!introduction.trim())
      next.introduction = "어떤 상품을 파는지 소개해 주세요.";
    if (business && !isValidBusinessNumber(businessNumber))
      next.businessNumber = "사업자등록번호 10자리를 다시 확인해 주세요.";
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
            연락처나 외부 링크는 적을 수 없어요.{" "}
            <span className="num">{introduction.length}</span>/
            {INTRODUCTION_MAX}
          </span>
        }
      />
      <fieldset className="flex flex-col gap-3">
        <legend className="mb-1.5 text-label font-semibold text-neutral-900">
          판매자 유형
        </legend>
        <label className="flex items-center gap-2 text-body">
          <input
            type="checkbox"
            checked={business}
            onChange={(e) => {
              setBusiness(e.target.checked);
              setErrors((prev) => ({ ...prev, businessNumber: undefined }));
            }}
            className="size-4 accent-brand-500"
          />
          사업자로 판매해요 (승인되면 프로필에 사업자 배지가 붙어요)
        </label>
        {business && (
          <div className="w-[320px]">
            <Input
              label="사업자등록번호"
              placeholder="예) 123-45-67890"
              inputMode="numeric"
              maxLength={12}
              value={businessNumber}
              onChange={(e) => {
                setBusinessNumber(e.target.value);
                setErrors((prev) => ({ ...prev, businessNumber: undefined }));
              }}
              error={errors.businessNumber}
              hint="숫자 10자리. 관리자가 심사할 때 확인하고, 다른 사람에게는 번호를 보여 주지 않아요."
            />
          </div>
        )}
      </fieldset>
      {apply.isError && !isErrorCode(apply.error, "SELLER_007") && (
        <Notice tone="error">{errorMessage(apply.error)}</Notice>
      )}
      <Button type="submit" className="self-start" loading={apply.isPending}>
        {previous ? "다시 신청하기" : "신청하기"}
      </Button>
    </form>
  );
}
