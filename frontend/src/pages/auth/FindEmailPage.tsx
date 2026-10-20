import { useState } from 'react';
import { Link } from 'react-router';
import { api, call, isErrorCode, type Schema } from '@/api';
import { VerificationField } from '@/components/auth/VerificationField';
import { AuthCard } from '@/components/layout/PageHeader';
import { Notice, buttonVariants } from '@/components/ui';
import { formatDate } from '@/lib/format';

type Result = { found: true; data: Schema<'FindEmailResponse'> } | { found: false };

export function FindEmailPage() {
  const [result, setResult] = useState<Result | null>(null);

  if (result) {
    return (
      <AuthCard title="이메일 찾기">
        {result.found ? (
          <div className="flex flex-col gap-1 rounded-md bg-neutral-50 px-4 py-5 text-center">
            <p className="text-label text-neutral-500">가입한 이메일</p>
            <p className="text-title-md font-bold">{result.data.maskedEmail}</p>
            {result.data.joinedAt && <p className="text-caption text-neutral-500">가입일 {formatDate(result.data.joinedAt)}</p>}
          </div>
        ) : (
          <Notice tone="info">가입된 계정이 없습니다.</Notice>
        )}
        <div className="grid grid-cols-2 gap-2">
          <Link to="/login" className={buttonVariants({ variant: 'primary' })}>
            로그인하기
          </Link>
          <Link to="/password-reset" className={buttonVariants({ variant: 'outline' })}>
            비밀번호 재설정
          </Link>
        </div>
      </AuthCard>
    );
  }

  return (
    <AuthCard title="이메일 찾기" description="가입할 때 인증한 휴대폰 번호로 이메일을 찾아요.">
      <VerificationField
        kind="phone"
        verified={false}
        sendCode={async (phoneNumber) => {
          const data = await call(api.POST('/api/v1/auth/find-email/code', { body: { phoneNumber } }));
          return data?.expiresIn ?? 300;
        }}
        verify={async (phoneNumber, code) => {
          try {
            const data = await call(api.POST('/api/v1/auth/find-email/verify', { body: { phoneNumber, code } }));
            setResult({ found: true, data: data ?? {} });
          } catch (e) {
            // 인증은 됐지만 그 번호로 가입한 회원이 없는 경우
            if (isErrorCode(e, 'USER_003')) {
              setResult({ found: false });
              return;
            }
            throw e;
          }
        }}
      />
    </AuthCard>
  );
}
