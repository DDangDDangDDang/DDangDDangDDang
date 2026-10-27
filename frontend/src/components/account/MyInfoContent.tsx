import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { api, call, errorMessage, replaceSession } from '@/api';
import { Button, Card, CardBody, CardHeader, CardTitle, ErrorState, Input, Notice, Skeleton, buttonVariants } from '@/components/ui';
import { formatDate, formatDateTime, formatPhone } from '@/lib/format';

const myInfoQueryKey = ['users', 'me'] as const;

/** 닉네임 변경 주기 안내 문구용. 실제 제한은 서버(app.user.nickname-change-interval, 기본 30일)가 한다 */
const NICKNAME_CHANGE_INTERVAL_DAYS = 30;

/**
 * 내 정보: 이메일, 닉네임, 휴대폰 번호, 가입일 + 닉네임 변경 + 비밀번호 변경 + 판매자 인증.
 * 헤더의 내 닉네임을 누르면 뜨는 창(MyInfoDialog)과 /me 화면이 같이 쓴다.
 * onNavigate: 비밀번호 변경·판매자 인증처럼 다른 화면으로 갈 때 (창이면 창을 닫는다)
 */
export function MyInfoContent({ onNavigate }: { onNavigate?: () => void }) {
  const query = useQuery({
    queryKey: myInfoQueryKey,
    queryFn: () => call(api.GET('/api/v1/users/me')),
  });

  return (
    <div>
      <Card>
        <CardHeader>
          <CardTitle>계정</CardTitle>
        </CardHeader>
        <CardBody>
          {query.isPending ? (
            <div className="flex flex-col gap-4">
              {Array.from({ length: 4 }, (_, i) => (
                <Skeleton key={i} className="h-6" />
              ))}
            </div>
          ) : query.isError ? (
            <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
          ) : (
            <dl className="grid grid-cols-[120px_1fr] items-center gap-y-4 text-body">
              <dt className="text-neutral-500">이메일</dt>
              <dd>{query.data?.email ?? '-'}</dd>
              <dt className="text-neutral-500">닉네임</dt>
              <dd>
                <NicknameEditor current={query.data?.nickname ?? ''} availableAt={query.data?.nicknameChangeAvailableAt} />
              </dd>
              <dt className="text-neutral-500">휴대폰 번호</dt>
              <dd className="num">{query.data?.phoneNumber ? formatPhone(query.data.phoneNumber) : '-'}</dd>
              <dt className="text-neutral-500">가입일</dt>
              <dd>{query.data?.createdAt ? formatDate(query.data.createdAt) : '-'}</dd>
            </dl>
          )}
        </CardBody>
      </Card>

      <Card className="mt-4">
        <CardHeader>
          <CardTitle>비밀번호</CardTitle>
        </CardHeader>
        <CardBody className="flex items-center justify-between gap-4">
          <p className="text-body text-neutral-500">비밀번호를 바꾸면 모든 기기에서 로그아웃되고 다시 로그인해야 해요.</p>
          <Link to="/password-reset" onClick={onNavigate} className={buttonVariants({ variant: 'outline' })}>
            비밀번호 변경
          </Link>
        </CardBody>
      </Card>

      <Card className="mt-4">
        <CardHeader>
          <CardTitle>판매자 인증</CardTitle>
        </CardHeader>
        <CardBody className="flex items-center justify-between gap-4">
          <p className="text-body text-neutral-500">인증을 받으면 내 프로필에 판매자 인증 마크가 붙고, 시청자가 판매자 프로필(방송·경매 기록, 거래 신뢰도)을 볼 수 있어요.</p>
          <Link to="/me/seller" onClick={onNavigate} className={buttonVariants({ variant: 'outline', className: 'shrink-0' })}>
            판매자 인증
          </Link>
        </CardBody>
      </Card>
    </div>
  );
}

/** availableAt: 다음에 닉네임을 바꿀 수 있는 시각. 없으면 지금 바꿀 수 있다 */
function NicknameEditor({ current, availableAt }: { current: string; availableAt?: string }) {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [nickname, setNickname] = useState(current);
  const [done, setDone] = useState(false);

  const mutation = useMutation({
    mutationFn: (next: string) => call(api.PATCH('/api/v1/users/me', { body: { nickname: next } })),
    onSuccess: (data) => {
      // 토큰에 닉네임이 들어 있어서, 서버가 바뀐 닉네임이 담긴 새 토큰을 준다.
      if (data?.accessToken && data.user) replaceSession(data.accessToken, data.user);
      queryClient.setQueryData(myInfoQueryKey, data?.user);
      setEditing(false);
      setDone(true);
    },
  });

  if (!editing) {
    return (
      <div className="flex flex-col gap-2">
        <div className="flex items-center gap-3">
          <span className="font-semibold">{current}</span>
          <Button
            variant="outline"
            size="sm"
            disabled={Boolean(availableAt)}
            onClick={() => {
              setNickname(current);
              setDone(false);
              mutation.reset();
              setEditing(true);
            }}
          >
            변경
          </Button>
        </div>
        {availableAt && <p className="text-caption text-neutral-500">{formatDateTime(availableAt)}부터 다시 바꿀 수 있어요.</p>}
        {done && <Notice tone="success">닉네임을 변경했어요.</Notice>}
      </div>
    );
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate(nickname);
  }

  return (
    <form onSubmit={handleSubmit} className="flex items-start gap-2">
      <div className="flex-1">
        <Input
          label="새 닉네임"
          hideLabel
          autoFocus
          value={nickname}
          maxLength={10}
          hint={`2~10자로 입력해 주세요. 바꾸면 ${NICKNAME_CHANGE_INTERVAL_DAYS}일 동안 다시 바꿀 수 없어요.`}
          error={mutation.isError ? errorMessage(mutation.error) : undefined}
          onChange={(e) => setNickname(e.target.value)}
        />
      </div>
      <Button type="submit" loading={mutation.isPending} disabled={nickname.trim().length < 2}>
        저장
      </Button>
      <Button variant="ghost" onClick={() => setEditing(false)} disabled={mutation.isPending}>
        취소
      </Button>
    </form>
  );
}
