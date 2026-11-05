import { useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { api, call, errorMessage, isErrorCode, replaceSession } from '@/api';
import { VerificationField } from '@/components/auth/VerificationField';
import { Button, Dialog, Input, Notice, buttonVariants } from '@/components/ui';
import { myInfoQueryKey, type MyInfo } from './my-info-query';

type Kind = 'email' | 'phone';

const TEXT: Record<Kind, { title: string; description: string; done: string }> = {
  email: {
    title: '이메일 변경',
    description: '새 이메일로 받은 인증번호를 입력하면 바로 바뀌어요. 다음 로그인부터 새 이메일을 써 주세요.',
    done: '이메일을 바꿨어요.',
  },
  phone: {
    title: '휴대폰 번호 변경',
    description: '새 번호로 받은 인증번호를 입력하면 바로 바뀌어요. 이메일 찾기도 새 번호로 해요.',
    done: '휴대폰 번호를 바꿨어요.',
  },
};

/**
 * 이메일·휴대폰 번호 변경 창.
 * 1. 본인 확인: 비밀번호(소셜 가입 회원은 지금 휴대폰 번호로 받은 인증번호). 로그인 상태만 빼앗긴 사람이 바꾸지 못하게 한다.
 * 2. 새 이메일·번호로 인증번호를 받아 확인하면 바뀐다.
 */
export function ContactChangeDialog({ kind, me, open, onClose }: { kind: Kind; me: MyInfo | undefined; open: boolean; onClose: () => void }) {
  return (
    <Dialog open={open} title={TEXT[kind].title} onClose={onClose} className="w-[520px]">
      {/* 창을 열 때마다 새로 그려 이전 입력·인증 상태가 남지 않게 한다. */}
      {open && <ContactChangeForm kind={kind} me={me} onClose={onClose} />}
    </Dialog>
  );
}

function ContactChangeForm({ kind, me, onClose }: { kind: Kind; me: MyInfo | undefined; onClose: () => void }) {
  const queryClient = useQueryClient();
  // 본인 확인으로 받은 토큰 (10분, 한 번 바꾸면 사라짐)
  const [reauthToken, setReauthToken] = useState<string>();
  // 본인 확인이 만료돼 다시 확인해야 할 때 보여줄 안내
  const [reauthNotice, setReauthNotice] = useState<string>();
  const [changed, setChanged] = useState(false);
  const text = TEXT[kind];

  async function sendCode(target: string) {
    const response =
      kind === 'email'
        ? await call(api.POST('/api/v1/users/me/email/code', { body: { email: target } }))
        : await call(api.POST('/api/v1/users/me/phone-number/code', { body: { phoneNumber: target } }));
    return response?.expiresIn ?? 300;
  }

  // 인증번호 확인과 변경을 한 번에 한다. (서버가 인증번호를 확인하고 바로 바꾼다)
  async function change(target: string, code: string) {
    if (!reauthToken) return;
    try {
      if (kind === 'email') {
        const data = await call(api.PUT('/api/v1/users/me/email', { body: { email: target, code, reauthToken } }));
        // 토큰에 이메일이 들어 있어서, 서버가 바뀐 이메일이 담긴 새 토큰을 준다.
        if (data?.accessToken && data.user) replaceSession(data.accessToken, data.user);
        queryClient.setQueryData<MyInfo>(myInfoQueryKey, data?.user);
      } else {
        const data = await call(api.PUT('/api/v1/users/me/phone-number', { body: { phoneNumber: target, code, reauthToken } }));
        queryClient.setQueryData<MyInfo>(myInfoQueryKey, data);
      }
      setChanged(true);
    } catch (e) {
      // 본인 확인이 만료(10분)됐으면 본인 확인부터 다시 한다. 서버는 이때 인증번호를 쓰지 않는다.
      if (isErrorCode(e, 'VERIFY_004')) {
        setReauthToken(undefined);
        setReauthNotice('본인 확인 시간이 지났어요. 다시 확인해 주세요.');
        return;
      }
      throw e;
    }
  }

  return (
    <div className="flex flex-col gap-4">
      {reauthToken === undefined ? (
        <>
          {reauthNotice && <Notice tone="error">{reauthNotice}</Notice>}
          <ReauthStep me={me} onClose={onClose} onDone={(token) => {
            setReauthToken(token);
            setReauthNotice(undefined);
          }} />
        </>
      ) : (
        <>
          <p className="text-body text-neutral-500">{text.description}</p>
          <VerificationField kind={kind} sendCode={sendCode} verify={change} verified={changed} verifiedMessage={text.done} />
          <div className="flex justify-end">
            <Button variant={changed ? 'primary' : 'ghost'} onClick={onClose}>
              {changed ? '확인' : '닫기'}
            </Button>
          </div>
        </>
      )}
    </div>
  );
}

/** 바꾸기 전 본인 확인. 비밀번호가 있으면 비밀번호, 소셜 가입 회원은 지금 휴대폰 번호로 받은 인증번호 */
function ReauthStep({ me, onDone, onClose }: { me: MyInfo | undefined; onDone: (token: string) => void; onClose: () => void }) {
  const [password, setPassword] = useState('');
  const [checking, setChecking] = useState(false);
  const [error, setError] = useState<string>();

  async function handlePassword(event: FormEvent) {
    event.preventDefault();
    setChecking(true);
    setError(undefined);
    try {
      const data = await call(api.POST('/api/v1/users/me/reauth/password', { body: { password } }));
      if (data?.verificationToken) onDone(data.verificationToken);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setChecking(false);
    }
  }

  if (me?.hasPassword) {
    return (
      <form onSubmit={handlePassword} className="flex flex-col gap-4">
        <p className="text-body text-neutral-500">본인 확인을 위해 지금 비밀번호를 입력해 주세요.</p>
        <Input
          label="지금 비밀번호"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={error}
          required
        />
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            닫기
          </Button>
          <Button type="submit" loading={checking} disabled={!password}>
            확인
          </Button>
        </div>
      </form>
    );
  }

  if (!me?.phoneNumber) {
    return (
      <div className="flex flex-col gap-4">
        <Notice tone="info">
          본인 확인에 쓸 비밀번호와 휴대폰 번호가 없어요. 비밀번호 찾기로 비밀번호를 만든 뒤 다시 시도해 주세요.
        </Notice>
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            닫기
          </Button>
          <Link to="/password-reset" onClick={onClose} className={buttonVariants()}>
            비밀번호 찾기
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <p className="text-body text-neutral-500">본인 확인을 위해 지금 휴대폰 번호로 인증번호를 받아 주세요.</p>
      <VerificationField
        kind="phone"
        fixedTarget={me.phoneNumber}
        verified={false}
        sendCode={async () => {
          const data = await call(api.POST('/api/v1/users/me/reauth/code'));
          return data?.expiresIn ?? 300;
        }}
        verify={async (_target, code) => {
          const data = await call(api.POST('/api/v1/users/me/reauth/verify', { body: { code } }));
          if (data?.verificationToken) onDone(data.verificationToken);
        }}
      />
      <div className="flex justify-end">
        <Button variant="ghost" onClick={onClose}>
          닫기
        </Button>
      </div>
    </div>
  );
}
