import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, call, replaceSession } from '@/api';
import { VerificationField } from '@/components/auth/VerificationField';
import { Button, Dialog } from '@/components/ui';
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

/** 이메일·휴대폰 번호 변경 창. 새 이메일·번호로 인증번호를 받아 확인하면 바뀐다. */
export function ContactChangeDialog({ kind, open, onClose }: { kind: Kind; open: boolean; onClose: () => void }) {
  return (
    <Dialog open={open} title={TEXT[kind].title} onClose={onClose} className="w-[520px]">
      {/* 창을 열 때마다 새로 그려 이전 입력·인증 상태가 남지 않게 한다. */}
      {open && <ContactChangeForm kind={kind} onClose={onClose} />}
    </Dialog>
  );
}

function ContactChangeForm({ kind, onClose }: { kind: Kind; onClose: () => void }) {
  const queryClient = useQueryClient();
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
    if (kind === 'email') {
      const data = await call(api.PUT('/api/v1/users/me/email', { body: { email: target, code } }));
      // 토큰에 이메일이 들어 있어서, 서버가 바뀐 이메일이 담긴 새 토큰을 준다.
      if (data?.accessToken && data.user) replaceSession(data.accessToken, data.user);
      queryClient.setQueryData<MyInfo>(myInfoQueryKey, data?.user);
    } else {
      const data = await call(api.PUT('/api/v1/users/me/phone-number', { body: { phoneNumber: target, code } }));
      queryClient.setQueryData<MyInfo>(myInfoQueryKey, data);
    }
    setChanged(true);
  }

  return (
    <div className="flex flex-col gap-4">
      <p className="text-body text-neutral-500">{text.description}</p>
      <VerificationField kind={kind} sendCode={sendCode} verify={change} verified={changed} verifiedMessage={text.done} />
      <div className="flex justify-end">
        <Button variant={changed ? 'primary' : 'ghost'} onClick={onClose}>
          {changed ? '확인' : '닫기'}
        </Button>
      </div>
    </div>
  );
}
