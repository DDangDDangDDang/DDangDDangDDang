import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ImagePlus } from 'lucide-react';
import { useEffect, useRef, useState, type ChangeEvent } from 'react';
import { api, call, errorMessage, useSession, type Schema } from '@/api';
import { uploadToSignedUrl } from '@/api/upload';
import { PageHeader } from '@/components/layout/PageHeader';
import { Badge, Button, Card, CardBody, ConfirmDialog, EmptyState, ErrorState, Input, Notice, Select, Skeleton } from '@/components/ui';
import {
  AD_IMAGE_TYPES,
  AD_PLACEMENT_LABEL,
  AD_SLIDE_MS,
  adImageProblem,
  adLinkProblem,
  recommendedAdImageSize,
  type AdPlacementCode,
} from '@/lib/ads';
import { formatDateTime } from '@/lib/format';

type AdItem = Schema<'AdResponse'>;

const QUERY_KEY = ['admin', 'ads'] as const;
const PLACEMENT_OPTIONS = (Object.keys(AD_PLACEMENT_LABEL) as AdPlacementCode[]).map((code) => ({ value: code, label: AD_PLACEMENT_LABEL[code] }));
const FILTER_OPTIONS = [{ value: '', label: '모든 자리' }, ...PLACEMENT_OPTIONS];

/**
 * 광고 관리 (관리자): 자리를 골라 이미지·링크·문구로 광고를 등록하고, 켜고 끄고 지운다.
 * 한 자리에 켜진 광고가 2개 이상이면 화면에서 6초마다 번갈아 보여 준다. (AdBanner)
 */
export function AdsAdminPage() {
  const session = useSession();
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<AdPlacementCode | ''>('');
  const [deleting, setDeleting] = useState<AdItem>();
  const [done, setDone] = useState<string>();

  const query = useQuery({
    queryKey: [...QUERY_KEY, filter],
    queryFn: () => call(api.GET('/api/v1/admin/ads', { params: { query: { placement: filter || undefined } } })),
    enabled: session?.user.role === 'ADMIN',
  });
  const ads = query.data ?? [];

  // 관리자 목록과 화면 배너(useAds) 둘 다 다시 불러온다.
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: QUERY_KEY });
    void queryClient.invalidateQueries({ queryKey: ['ads'] });
  };

  const toggle = useMutation({
    mutationFn: (ad: AdItem) =>
      call(api.PATCH('/api/v1/admin/ads/{adId}', { params: { path: { adId: ad.id ?? 0 } }, body: { enabled: !ad.enabled } })),
    onSuccess: (saved) => setDone(saved?.enabled ? '광고를 켰어요. 화면에 바로 나와요.' : '광고를 껐어요. 화면에서 내려갔어요.'),
    onSettled: refresh,
  });
  const remove = useMutation({
    mutationFn: (id: number) => call(api.DELETE('/api/v1/admin/ads/{adId}', { params: { path: { adId: id } } })),
    onSuccess: () => {
      setDeleting(undefined);
      setDone('광고를 지웠어요.');
    },
    onSettled: refresh,
  });

  if (session?.user.role !== 'ADMIN') {
    return <EmptyState title="관리자만 쓸 수 있는 화면이에요" description="광고는 관리자가 등록해요." />;
  }

  return (
    <div className="mx-auto flex max-w-[960px] flex-col gap-6">
      <PageHeader title="광고 관리" description="홈 상단과 왼쪽 메뉴의 배너 광고를 등록하고 켜고 꺼요." />
      <Notice>
        한 자리에 켜진 광고가 2개 이상이면 {AD_SLIDE_MS / 1000}초마다 다음 광고로 넘어가요. 화면에서 좌우 화살표로 넘기거나 일시정지할 수 있어요.
      </Notice>

      <AdCreateForm
        onCreated={(message) => {
          setDone(message);
          refresh();
        }}
      />

      <section aria-label="등록한 광고" className="flex flex-col gap-3">
        <div className="flex items-center justify-between gap-4">
          <h2 className="text-title-md font-bold">등록한 광고</h2>
          <div className="w-[220px]">
            <Select label="자리" hideLabel options={FILTER_OPTIONS} value={filter} onChange={(e) => setFilter(e.target.value as AdPlacementCode | '')} />
          </div>
        </div>
        {done && <Notice tone="success">{done}</Notice>}
        {toggle.isError && <Notice tone="error">{errorMessage(toggle.error)}</Notice>}
        <Card>
          {query.isPending ? (
            <div className="flex flex-col gap-2 p-4">
              <Skeleton className="h-20" />
              <Skeleton className="h-20" />
            </div>
          ) : query.isError ? (
            <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
          ) : ads.length === 0 ? (
            <EmptyState title="등록한 광고가 없어요" description="위에서 광고를 등록하면 바로 화면에 나와요." />
          ) : (
            <ul className="divide-y divide-neutral-100">
              {ads.map((ad) => (
                <li key={ad.id} className="flex items-center gap-4 px-5 py-4">
                  <img
                    src={ad.imageUrl}
                    alt=""
                    className={ad.placement === 'SIDEBAR' ? 'h-24 w-18 shrink-0 rounded-md object-cover' : 'h-12 w-48 shrink-0 rounded-md object-cover'}
                  />
                  <div className="flex min-w-0 flex-1 flex-col gap-1">
                    <span className="flex items-center gap-2">
                      <Badge variant="outline">{ad.placement ? AD_PLACEMENT_LABEL[ad.placement] : '-'}</Badge>
                      <Badge variant={ad.enabled ? 'info' : 'neutral'}>{ad.enabled ? '켜짐' : '꺼짐'}</Badge>
                    </span>
                    <strong className="truncate text-body font-semibold">{ad.altText}</strong>
                    <a href={ad.linkUrl} target="_blank" rel="noopener noreferrer" className="truncate text-caption text-neutral-500 underline">
                      {ad.linkUrl}
                    </a>
                    {ad.createdAt && <span className="text-caption text-neutral-500">{formatDateTime(ad.createdAt)} 등록</span>}
                  </div>
                  <div className="flex shrink-0 gap-2">
                    <Button size="sm" variant="outline" loading={toggle.isPending && toggle.variables?.id === ad.id} onClick={() => toggle.mutate(ad)}>
                      {ad.enabled ? '끄기' : '켜기'}
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => {
                        remove.reset();
                        setDeleting(ad);
                      }}
                    >
                      삭제
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </section>

      <ConfirmDialog
        open={deleting !== undefined}
        title="광고를 지울까요?"
        description={
          <div className="flex flex-col gap-2">
            <p>지운 광고는 되돌릴 수 없어요. 잠시 내리려면 [끄기]를 쓰세요.</p>
            {remove.isError && <Notice tone="error">{errorMessage(remove.error)}</Notice>}
          </div>
        }
        confirmLabel="삭제"
        cancelLabel="닫기"
        loading={remove.isPending}
        onConfirm={() => deleting?.id && remove.mutate(deleting.id)}
        onCancel={() => setDeleting(undefined)}
      />
    </div>
  );
}

/** 광고 등록: 자리 → 이미지 → 링크 → 문구. 이미지는 업로드 URL로 바로 올린 뒤 받은 키로 등록한다. */
function AdCreateForm({ onCreated }: { onCreated: (message: string) => void }) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [placement, setPlacement] = useState<AdPlacementCode>('HOME_TOP');
  const [file, setFile] = useState<File>();
  const [preview, setPreview] = useState<string>();
  const [linkUrl, setLinkUrl] = useState('');
  const [altText, setAltText] = useState('');
  const [errors, setErrors] = useState<{ file?: string; linkUrl?: string; altText?: string }>({});

  // 고른 이미지 미리보기. 바꾸거나 화면을 떠나면 임시 주소를 정리한다.
  useEffect(() => {
    if (!file) {
      setPreview(undefined);
      return;
    }
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  const create = useMutation({
    mutationFn: async (input: { file: File; placement: AdPlacementCode; linkUrl: string; altText: string }) => {
      const upload = await call(
        api.POST('/api/v1/admin/ads/image-uploads', { body: { contentType: input.file.type, contentLength: input.file.size } }),
      );
      if (!upload?.uploadUrl || !upload.imageKey) throw new Error('업로드 주소를 받지 못했어요. 다시 시도해 주세요.');
      await uploadToSignedUrl(upload.uploadUrl, input.file);
      return call(
        api.POST('/api/v1/admin/ads', {
          body: { placement: input.placement, imageKey: upload.imageKey, linkUrl: input.linkUrl.trim(), altText: input.altText.trim() },
        }),
      );
    },
    onSuccess: (_, input) => {
      onCreated(`${AD_PLACEMENT_LABEL[input.placement]}에 광고를 등록했어요. 바로 화면에 나와요.`);
      setFile(undefined);
      setLinkUrl('');
      setAltText('');
      setErrors({});
      if (inputRef.current) inputRef.current.value = '';
    },
  });

  const handleFile = (e: ChangeEvent<HTMLInputElement>) => {
    const picked = e.target.files?.[0];
    if (!picked) return;
    const problem = adImageProblem(picked);
    setErrors((prev) => ({ ...prev, file: problem }));
    setFile(problem ? undefined : picked);
  };

  const submit = () => {
    const next = {
      file: file ? undefined : '광고 이미지를 골라 주세요.',
      linkUrl: adLinkProblem(linkUrl),
      altText: altText.trim() ? (altText.trim().length > 100 ? '광고 문구는 100자 이하로 입력해 주세요.' : undefined) : '광고 문구를 입력해 주세요.',
    };
    setErrors(next);
    if (next.file || next.linkUrl || next.altText || !file) return;
    create.mutate({ file, placement, linkUrl, altText });
  };

  return (
    <Card>
      <CardBody className="flex flex-col gap-4 pt-5">
        <h2 className="text-title-md font-bold">새 광고 등록</h2>
        <div className="grid grid-cols-2 gap-4">
          <Select
            label="광고 자리"
            options={PLACEMENT_OPTIONS}
            value={placement}
            onChange={(e) => setPlacement(e.target.value as AdPlacementCode)}
          />
          <div className="flex flex-col gap-1.5">
            <span className="text-label font-semibold text-neutral-900">광고 이미지</span>
            <input
              ref={inputRef}
              type="file"
              accept={AD_IMAGE_TYPES.join(',')}
              className="sr-only"
              aria-label="광고 이미지 파일"
              tabIndex={-1}
              onChange={handleFile}
            />
            <Button variant="outline" onClick={() => inputRef.current?.click()} disabled={create.isPending}>
              <ImagePlus className="size-4" aria-hidden="true" />
              {file ? '다른 이미지 고르기' : '이미지 고르기'}
            </Button>
            <span className="text-caption text-neutral-500">
              권장 <span className="num">{recommendedAdImageSize(placement)}</span> · JPG, PNG, WEBP, GIF · 5MB 이하
            </span>
            {errors.file && <span className="text-caption text-red-600">{errors.file}</span>}
          </div>
        </div>
        {preview && (
          <img
            src={preview}
            alt="고른 광고 이미지 미리보기"
            className={placement === 'SIDEBAR' ? 'h-40 w-30 rounded-md object-cover' : 'h-16 w-full max-w-[512px] rounded-md object-cover'}
          />
        )}
        <Input
          label="광고 링크"
          placeholder="https://"
          hint="광고를 누르면 새 탭으로 열 주소예요."
          value={linkUrl}
          onChange={(e) => setLinkUrl(e.target.value)}
          error={errors.linkUrl}
        />
        <Input
          label="광고 문구"
          placeholder="예) 땅땅땅 가을 경매 페스티벌"
          hint="이미지를 볼 수 없는 사람에게 읽어 주는 문구예요."
          maxLength={100}
          value={altText}
          onChange={(e) => setAltText(e.target.value)}
          error={errors.altText}
        />
        {create.isError && <Notice tone="error">{errorMessage(create.error)}</Notice>}
        <div className="flex justify-end">
          <Button loading={create.isPending} onClick={submit}>
            광고 등록
          </Button>
        </div>
      </CardBody>
    </Card>
  );
}
