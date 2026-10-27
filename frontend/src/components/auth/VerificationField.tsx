import { useState } from 'react';
import { errorMessage } from '@/api';
import { Button, Input, Notice } from '@/components/ui';
import { formatRemaining, onlyDigits } from '@/lib/format';
import { useCountdown } from '@/lib/use-countdown';

/** [인증번호 받기]를 다시 누를 수 있을 때까지 기다리는 시간 (서버도 1분에 한 번만 허용) */
const RESEND_COOLDOWN_SECONDS = 60;

type VerificationFieldProps = {
  /** 인증번호를 받을 곳: 휴대폰 번호 또는 이메일 */
  kind: 'phone' | 'email';
  /** 인증번호를 보낸다. 응답의 유효시간(초)을 돌려준다. */
  sendCode: (target: string) => Promise<number>;
  /** 인증번호를 확인한다. 실패하면 ApiError를 던진다. */
  verify: (target: string, code: string) => Promise<void>;
  /** 인증을 마쳤는지. true면 입력을 잠그고 완료 안내를 보여준다. */
  verified: boolean;
  /** 받는 곳(번호·이메일)을 바꾸면 호출된다. 이전 인증을 무효로 돌려야 할 때 쓴다. */
  onTargetChange?: (target: string) => void;
  /** 인증번호를 보낸 뒤 보여줄 안내 */
  sentMessage?: string;
  verifiedMessage?: string;
};

/**
 * 받는 곳 입력 + [인증번호 받기] → 인증번호 입력 + [확인].
 * - 인증번호 유효시간(서버 응답, 보통 5분) 타이머
 * - [인증번호 받기]는 1분 동안 비활성화
 * 회원가입·이메일 찾기(휴대폰), 비밀번호 재설정(이메일)에서 쓴다.
 */
export function VerificationField({
  kind,
  sendCode,
  verify,
  verified,
  onTargetChange,
  sentMessage = '인증번호를 보냈어요.',
  verifiedMessage = '인증을 완료했어요.',
}: VerificationFieldProps) {
  const [target, setTarget] = useState('');
  const [code, setCode] = useState('');
  const [expiresAt, setExpiresAt] = useState<number | null>(null);
  const [resendAt, setResendAt] = useState<number | null>(null);
  const [sending, setSending] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [sendError, setSendError] = useState<string>();
  const [verifyError, setVerifyError] = useState<string>();

  // 인증이 풀리면(가입·재설정이 인증 만료 등으로 실패) 이미 쓴 인증번호·타이머를 지우고 [인증번호 받기]부터 다시 하게 한다.
  // 번호·이메일은 그대로 두어 바로 다시 받을 수 있다. 재요청 대기(1분)는 서버도 지키므로 남겨 둔다.
  const [prevVerified, setPrevVerified] = useState(verified);
  if (verified !== prevVerified) {
    setPrevVerified(verified);
    if (!verified) {
      setCode('');
      setExpiresAt(null);
      setVerifyError(undefined);
    }
  }

  const remaining = useCountdown(expiresAt);
  const resendWait = useCountdown(resendAt);
  const sent = expiresAt !== null;
  const expired = sent && remaining === 0;

  async function handleSend() {
    setSending(true);
    setSendError(undefined);
    setVerifyError(undefined);
    try {
      const expiresIn = await sendCode(target);
      const now = Date.now();
      setExpiresAt(now + expiresIn * 1000);
      setResendAt(now + RESEND_COOLDOWN_SECONDS * 1000);
      setCode('');
    } catch (error) {
      setSendError(errorMessage(error));
    } finally {
      setSending(false);
    }
  }

  async function handleVerify() {
    setVerifying(true);
    setVerifyError(undefined);
    try {
      await verify(target, code);
    } catch (error) {
      setVerifyError(errorMessage(error));
    } finally {
      setVerifying(false);
    }
  }

  const isPhone = kind === 'phone';

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-start gap-2">
        <div className="flex-1">
          <Input
            label={isPhone ? '휴대폰 번호' : '이메일'}
            type={isPhone ? 'tel' : 'email'}
            inputMode={isPhone ? 'numeric' : 'email'}
            autoComplete={isPhone ? 'tel-national' : 'email'}
            placeholder={isPhone ? '01012345678' : 'name@example.com'}
            hint={isPhone ? '하이픈 없이 숫자만 입력해 주세요.' : undefined}
            value={target}
            disabled={verified}
            error={sendError}
            onChange={(e) => {
              const next = isPhone ? onlyDigits(e.target.value).slice(0, 11) : e.target.value.trim();
              setTarget(next);
              onTargetChange?.(next);
            }}
          />
        </div>
        <Button
          variant="outline"
          className="mt-[26px] w-[136px]"
          onClick={handleSend}
          loading={sending}
          disabled={verified || !target || resendWait > 0}
        >
          {resendWait > 0 ? `다시 받기 ${formatRemaining(resendWait)}` : sent ? '다시 받기' : '인증번호 받기'}
        </Button>
      </div>

      {sent && !verified && (
        <>
          <Notice tone="info">{sentMessage}</Notice>
          <div className="flex items-start gap-2">
            <div className="flex-1">
              <Input
                label="인증번호"
                inputMode="numeric"
                autoComplete="one-time-code"
                placeholder="6자리 숫자"
                maxLength={6}
                value={code}
                onChange={(e) => setCode(onlyDigits(e.target.value).slice(0, 6))}
                error={expired ? '인증번호가 만료됐어요. 다시 받아 주세요.' : verifyError}
                hint={
                  <span>
                    남은 시간 <span className="num font-bold text-brand-700">{formatRemaining(remaining)}</span>
                  </span>
                }
              />
            </div>
            <Button
              variant="secondary"
              className="mt-[26px] w-[136px]"
              onClick={handleVerify}
              loading={verifying}
              disabled={code.length !== 6 || expired}
            >
              확인
            </Button>
          </div>
        </>
      )}

      {verified && <Notice tone="success">{verifiedMessage}</Notice>}
    </div>
  );
}
