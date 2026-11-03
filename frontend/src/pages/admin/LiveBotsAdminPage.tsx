import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Film, ImagePlus } from 'lucide-react';
import { useEffect, useRef, useState, type ChangeEvent } from 'react';
import { Link } from 'react-router';
import { api, call, errorMessage, useSession, type Schema } from '@/api';
import { uploadToSignedUrl } from '@/api/upload';
import { PageHeader } from '@/components/layout/PageHeader';
import { SellerAvatar } from '@/components/seller/SellerAvatar';
import { Badge, Button, buttonVariants, Card, CardBody, ConfirmDialog, Dialog, EmptyState, ErrorState, Input, Notice, Skeleton, Textarea } from '@/components/ui';
import { LIVE_BOT_VIDEO_TYPES, liveBotVideoProblem } from '@/lib/bot-video';
import { formatDateTime } from '@/lib/format';
import { PROFILE_IMAGE_TYPES, profileImageProblem } from '@/lib/profile-image';
import { HashtagField } from '@/pages/studio/HashtagField';

type LiveBot = Schema<'LiveBotResponse'>;

const QUERY_KEY = ['admin', 'live-bots'] as const;

/**
 * 라이브 봇 관리 (관리자): 닉네임과 녹화 영상으로 봇을 만들고, 봇 방송을 켜고 끈다.
 * 봇은 로그인할 수 없는 판매자 계정이라 카메라 방송은 할 수 없고, 올린 녹화 영상만 반복해서 내보낸다.
 * 방송 카드·시청 화면에는 사람 판매자의 녹화 영상 방송과 똑같이 보인다. (녹화 영상 표시, 시청자 수는 실제로 들어온 사람만)
 */
export function LiveBotsAdminPage() {
  const session = useSession();
  const queryClient = useQueryClient();
  const [starting, setStarting] = useState<LiveBot>();
  const [deleting, setDeleting] = useState<LiveBot>();
  const [editingImageId, setEditingImageId] = useState<number>();
  const [done, setDone] = useState<string>();
  const setEditingImage = (bot: LiveBot) => setEditingImageId(bot.id);

  const query = useQuery({
    queryKey: QUERY_KEY,
    queryFn: () => call(api.GET('/api/v1/admin/live-bots')),
    enabled: session?.user.role === 'ADMIN',
  });
  const bots = query.data ?? [];

  // 봇 목록과 방송 목록(홈·방송 목록 카드)을 다시 불러온다.
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: QUERY_KEY });
    void queryClient.invalidateQueries({ queryKey: ['live-streams'] });
  };

  const end = useMutation({
    mutationFn: (bot: LiveBot) => call(api.POST('/api/v1/admin/live-bots/{botId}/end', { params: { path: { botId: bot.id ?? 0 } } })),
    onSuccess: (_, bot) => setDone(`${bot.nickname ?? '봇'}의 방송을 껐어요.`),
    onSettled: refresh,
  });
  const remove = useMutation({
    mutationFn: (id: number) => call(api.DELETE('/api/v1/admin/live-bots/{botId}', { params: { path: { botId: id } } })),
    onSuccess: () => {
      setDeleting(undefined);
      setDone('라이브 봇을 지웠어요.');
    },
    onSettled: refresh,
  });

  if (session?.user.role !== 'ADMIN') {
    return <EmptyState title="관리자만 쓸 수 있는 화면이에요" description="라이브 봇은 관리자가 만들어요." />;
  }

  return (
    <div className="mx-auto flex max-w-[960px] flex-col gap-6">
      <PageHeader title="라이브 봇" description="녹화 영상으로 방송하는 봇 판매자를 만들고, 방송을 켜고 꺼요." />
      <Notice>
        봇 방송은 올린 영상을 끝없이 반복해요. 시청 화면은 방송 시작 시각에 맞춰 영상을 틀어 누가 들어와도 같은 장면을 봐요. 봇 계정으로는 로그인할 수
        없어서 카메라 방송은 할 수 없고, 관리자가 끌 때까지 켜져 있어요.
      </Notice>

      <LiveBotCreateForm
        onCreated={(message) => {
          setDone(message);
          refresh();
        }}
      />

      <section aria-label="라이브 봇 목록" className="flex flex-col gap-3">
        <h2 className="text-title-md font-bold">만든 봇</h2>
        {done && <Notice tone="success">{done}</Notice>}
        {end.isError && <Notice tone="error">{errorMessage(end.error)}</Notice>}
        <Card>
          {query.isPending ? (
            <div className="flex flex-col gap-2 p-4">
              <Skeleton className="h-24" />
              <Skeleton className="h-24" />
            </div>
          ) : query.isError ? (
            <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
          ) : bots.length === 0 ? (
            <EmptyState title="만든 봇이 없어요" description="위에서 닉네임과 녹화 영상으로 봇을 만들면 여기서 방송을 켤 수 있어요." />
          ) : (
            <ul className="divide-y divide-neutral-100">
              {bots.map((bot) => {
                const live = bot.liveStreamId != null;
                return (
                  <li key={bot.id} className="flex items-center gap-4 px-5 py-4">
                    <video src={bot.videoUrl} preload="metadata" muted aria-hidden="true" className="aspect-video w-40 shrink-0 rounded-md bg-stage object-cover" />
                    <div className="flex min-w-0 flex-1 flex-col gap-1">
                      <span className="flex items-center gap-2">
                        <SellerAvatar name={bot.nickname} imageUrl={bot.profileImageUrl} size="sm" />
                        <strong className="truncate text-body font-semibold">{bot.nickname ?? '알 수 없는 계정'}</strong>
                        <Badge variant={live ? 'live' : 'neutral'}>{live ? '방송 중' : '꺼짐'}</Badge>
                      </span>
                      {live ? (
                        <span className="truncate text-label text-neutral-700">
                          {bot.liveTitle}
                          {bot.liveStartedAt && <span className="text-neutral-500"> · {formatDateTime(bot.liveStartedAt)} 시작</span>}
                        </span>
                      ) : (
                        bot.createdAt && <span className="text-caption text-neutral-500">{formatDateTime(bot.createdAt)} 만듦</span>
                      )}
                    </div>
                    <div className="flex shrink-0 gap-2">
                      <Button size="sm" variant="outline" onClick={() => setEditingImage(bot)}>
                        프로필 사진
                      </Button>
                      <Link to={`/admin/live-bots/${bot.id}`} className={buttonVariants({ variant: 'outline', size: 'sm' })}>
                        판매 관리
                      </Link>
                      {live ? (
                        <>
                          <Link to={`/lives/${bot.liveStreamId}`} className={buttonVariants({ variant: 'outline', size: 'sm' })}>
                            방송 보기
                          </Link>
                          <Button size="sm" variant="outline" loading={end.isPending && end.variables?.id === bot.id} onClick={() => end.mutate(bot)}>
                            방송 끄기
                          </Button>
                        </>
                      ) : (
                        <Button size="sm" onClick={() => setStarting(bot)}>
                          방송 켜기
                        </Button>
                      )}
                      <Button
                        size="sm"
                        variant="ghost"
                        disabled={live}
                        title={live ? '방송을 끈 뒤 지울 수 있어요' : undefined}
                        onClick={() => {
                          remove.reset();
                          setDeleting(bot);
                        }}
                      >
                        삭제
                      </Button>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </Card>
      </section>

      {starting && (
        <LiveBotStartDialog
          bot={starting}
          onClose={() => setStarting(undefined)}
          onStarted={(message) => {
            setStarting(undefined);
            setDone(message);
            refresh();
          }}
        />
      )}

      {editingImageId !== undefined && (
        // 목록을 다시 불러오면 바뀐 사진이 창에도 바로 보이도록 목록에서 찾는다.
        <BotProfileImageDialog
          bot={bots.find((bot) => bot.id === editingImageId) ?? { id: editingImageId }}
          onClose={() => setEditingImageId(undefined)}
          onChanged={refresh}
        />
      )}

      <ConfirmDialog
        open={deleting !== undefined}
        title="라이브 봇을 지울까요?"
        description={
          <div className="flex flex-col gap-2">
            <p>봇과 녹화 영상을 지워요. 지난 방송 기록에 남은 봇 닉네임은 그대로 둬요.</p>
            <p>진행 중인 경매나 발송 전 주문이 남아 있으면 지울 수 없어요. 지우면 그 경매와 주문을 다룰 수 없게 되기 때문이에요.</p>
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

/** 봇 만들기: 닉네임 → 녹화 영상. 영상은 업로드 URL로 바로 올린 뒤 받은 키로 만든다. */
function LiveBotCreateForm({ onCreated }: { onCreated: (message: string) => void }) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [nickname, setNickname] = useState('');
  const [file, setFile] = useState<File>();
  const [preview, setPreview] = useState<string>();
  const [errors, setErrors] = useState<{ nickname?: string; file?: string }>({});
  // 이미 올린 영상. 닉네임이 겹쳐 만들기가 실패한 뒤 다시 누르면 큰 영상을 또 올리지 않고 이 키를 쓴다.
  const uploadedRef = useRef<{ file: File; videoKey: string } | undefined>(undefined);

  // 고른 영상 미리보기. 바꾸거나 화면을 떠나면 임시 주소를 정리한다.
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
    mutationFn: async (input: { nickname: string; file: File }) => {
      let videoKey = uploadedRef.current?.file === input.file ? uploadedRef.current.videoKey : undefined;
      if (!videoKey) {
        const upload = await call(
          api.POST('/api/v1/admin/live-bots/video-uploads', { body: { contentType: input.file.type, contentLength: input.file.size } }),
        );
        if (!upload?.uploadUrl || !upload.videoKey) throw new Error('업로드 주소를 받지 못했어요. 다시 시도해 주세요.');
        await uploadToSignedUrl(upload.uploadUrl, input.file);
        videoKey = upload.videoKey;
        uploadedRef.current = { file: input.file, videoKey };
      }
      return call(api.POST('/api/v1/admin/live-bots', { body: { nickname: input.nickname, videoKey } }));
    },
    onSuccess: (_, input) => {
      uploadedRef.current = undefined;
      onCreated(`${input.nickname} 봇을 만들었어요. [방송 켜기]로 방송을 시작하세요.`);
      setNickname('');
      setFile(undefined);
      setErrors({});
      if (inputRef.current) inputRef.current.value = '';
    },
  });

  const handleFile = (e: ChangeEvent<HTMLInputElement>) => {
    const picked = e.target.files?.[0];
    if (!picked) return;
    const problem = liveBotVideoProblem(picked);
    setErrors((prev) => ({ ...prev, file: problem }));
    setFile(problem ? undefined : picked);
  };

  const submit = () => {
    const name = nickname.trim();
    const next = {
      nickname: name.length < 2 || name.length > 10 ? '닉네임은 2~10자로 입력해 주세요.' : undefined,
      file: file ? undefined : '녹화 영상을 골라 주세요.',
    };
    setErrors(next);
    if (next.nickname || next.file || !file) return;
    create.mutate({ nickname: name, file });
  };

  return (
    <Card>
      <CardBody className="flex flex-col gap-4 pt-5">
        <h2 className="text-title-md font-bold">새 봇 만들기</h2>
        <div className="grid grid-cols-2 gap-4">
          <Input
            label="닉네임"
            placeholder="예) 땅땅마켓"
            hint="방송 카드와 시청 화면에 판매자 이름으로 보여요."
            maxLength={10}
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            error={errors.nickname}
          />
          <div className="flex flex-col gap-1.5">
            <span className="text-label font-semibold text-neutral-900">녹화 영상</span>
            <input
              ref={inputRef}
              type="file"
              accept={LIVE_BOT_VIDEO_TYPES.join(',')}
              className="sr-only"
              aria-label="녹화 영상 파일"
              tabIndex={-1}
              onChange={handleFile}
            />
            <Button variant="outline" onClick={() => inputRef.current?.click()} disabled={create.isPending}>
              <Film className="size-4" aria-hidden="true" />
              {file ? '다른 영상 고르기' : '영상 고르기'}
            </Button>
            <span className="text-caption text-neutral-500">MP4, WEBM · 500MB 이하 · 끝나면 처음부터 다시 나와요</span>
            {errors.file && <span className="text-caption text-red-600">{errors.file}</span>}
          </div>
        </div>
        {preview && <video src={preview} controls muted aria-label="고른 녹화 영상 미리보기" className="aspect-video w-full max-w-[480px] rounded-md bg-stage" />}
        {create.isError && <Notice tone="error">{errorMessage(create.error)}</Notice>}
        <div className="flex justify-end">
          <Button loading={create.isPending} onClick={submit}>
            {create.isPending ? '영상 올리는 중' : '봇 만들기'}
          </Button>
        </div>
      </CardBody>
    </Card>
  );
}

/**
 * 봇 프로필 사진: 회원 프로필 사진과 같은 방식(업로드 URL → 저장소에 올리기 → 확정)과 규칙이다.
 * 방송 카드·시청 화면·판매자 프로필에 바로 보인다. 사진이 없으면 닉네임 첫 글자로 보인다.
 */
function BotProfileImageDialog({ bot, onClose, onChanged }: { bot: LiveBot; onClose: () => void; onChanged: () => void }) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [problem, setProblem] = useState<string>();
  const botId = bot.id ?? 0;

  const upload = useMutation({
    mutationFn: async (file: File) => {
      const signed = await call(
        api.POST('/api/v1/admin/live-bots/{botId}/profile-image/upload-url', {
          params: { path: { botId } },
          body: { contentType: file.type, contentLength: file.size },
        }),
      );
      if (!signed?.uploadUrl || !signed.imageKey) throw new Error('업로드 URL을 받지 못했어요.');
      await uploadToSignedUrl(signed.uploadUrl, file);
      return call(api.PUT('/api/v1/admin/live-bots/{botId}/profile-image', { params: { path: { botId } }, body: { imageKey: signed.imageKey } }));
    },
    onSuccess: onChanged,
  });
  const remove = useMutation({
    mutationFn: () => call(api.DELETE('/api/v1/admin/live-bots/{botId}/profile-image', { params: { path: { botId } } })),
    onSuccess: onChanged,
  });

  const handleFile = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    // 같은 파일을 다시 골라도 change가 일어나게 비운다.
    event.target.value = '';
    if (!file) return;
    const reason = profileImageProblem(file);
    setProblem(reason);
    upload.reset();
    remove.reset();
    if (!reason) upload.mutate(file);
  };

  const busy = upload.isPending || remove.isPending;
  const error = problem ?? (upload.isError ? errorMessage(upload.error) : remove.isError ? errorMessage(remove.error) : undefined);

  return (
    <Dialog open title={`${bot.nickname ?? '봇'} 프로필 사진`} description="방송 카드와 시청 화면에 판매자 사진으로 보여요." onClose={onClose} closeDisabled={busy}>
      <div className="flex flex-col gap-4">
        <div className="flex items-center gap-5">
          <SellerAvatar name={bot.nickname} imageUrl={bot.profileImageUrl} size="xl" />
          <div className="flex flex-col gap-2">
            <div className="flex items-center gap-2">
              <input
                ref={inputRef}
                type="file"
                accept={PROFILE_IMAGE_TYPES.join(',')}
                className="sr-only"
                aria-label="봇 프로필 사진 파일"
                tabIndex={-1}
                onChange={handleFile}
              />
              <Button variant="outline" size="sm" loading={upload.isPending} disabled={busy} onClick={() => inputRef.current?.click()}>
                <ImagePlus className="size-4" aria-hidden="true" />
                사진 변경
              </Button>
              {bot.profileImageUrl && (
                <Button
                  variant="ghost"
                  size="sm"
                  loading={remove.isPending}
                  disabled={busy}
                  onClick={() => {
                    // 앞서 바꾼 안내가 남지 않게 지운 뒤 지운다.
                    setProblem(undefined);
                    upload.reset();
                    remove.mutate();
                  }}
                >
                  사진 삭제
                </Button>
              )}
            </div>
            <p className="text-caption text-neutral-500">JPG, PNG, WEBP, GIF · 5MB 이하</p>
          </div>
        </div>
        {error && <Notice tone="error">{error}</Notice>}
        {upload.isSuccess && <Notice tone="success">프로필 사진을 바꿨어요.</Notice>}
        {remove.isSuccess && <Notice tone="success">프로필 사진을 지웠어요.</Notice>}
        <div className="flex justify-end">
          <Button variant="ghost" onClick={onClose} disabled={busy}>
            닫기
          </Button>
        </div>
      </div>
    </Dialog>
  );
}

/** 봇 방송 켜기: 제목·해시태그·설명을 정하면 바로 방송이 시작된다. */
function LiveBotStartDialog({ bot, onClose, onStarted }: { bot: LiveBot; onClose: () => void; onStarted: (message: string) => void }) {
  const [title, setTitle] = useState('');
  const [hashtags, setHashtags] = useState<string[]>([]);
  const [hashtagsValid, setHashtagsValid] = useState(true);
  const [description, setDescription] = useState('');
  const [titleError, setTitleError] = useState<string>();

  const start = useMutation({
    mutationFn: () =>
      call(
        api.POST('/api/v1/admin/live-bots/{botId}/start', {
          params: { path: { botId: bot.id ?? 0 } },
          body: { title: title.trim(), hashtags, description: description.trim() || undefined },
        }),
      ),
    onSuccess: () => onStarted(`${bot.nickname ?? '봇'}의 방송을 켰어요. 방송 목록에 바로 나와요.`),
  });

  const submit = () => {
    const error = title.trim() ? undefined : '방송 제목을 입력해 주세요.';
    setTitleError(error);
    if (error || !hashtagsValid) return;
    start.mutate();
  };

  return (
    <Dialog open title={`${bot.nickname ?? '봇'} 방송 켜기`} description="켜면 바로 방송 중이 되고, 구독자에게 방송 시작 알림이 가요." onClose={onClose} closeDisabled={start.isPending}>
      <div className="flex flex-col gap-4">
        <Input label="방송 제목" maxLength={100} value={title} onChange={(e) => setTitle(e.target.value)} error={titleError} />
        <HashtagField tags={hashtags} onChange={setHashtags} onValidityChange={setHashtagsValid} />
        <Textarea label="방송 설명 (선택)" maxLength={1000} rows={3} value={description} onChange={(e) => setDescription(e.target.value)} />
        {start.isError && <Notice tone="error">{errorMessage(start.error)}</Notice>}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose} disabled={start.isPending}>
            닫기
          </Button>
          <Button loading={start.isPending} onClick={submit}>
            방송 켜기
          </Button>
        </div>
      </div>
    </Dialog>
  );
}
