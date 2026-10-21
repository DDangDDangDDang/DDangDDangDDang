import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { errorMessage, login } from '@/api';
import { Button, Dialog, Input, Notice } from '@/components/ui';

type Props = {
  open: boolean;
  onClose: () => void;
  /** 이 창에서 로그인을 마쳤을 때. 하던 동작(채팅 입력, 입찰 등)으로 돌아간다. */
  onLoggedIn: () => void;
};

/**
 * 로그인이 필요한 동작(채팅·입찰·문의)을 로그인하지 않은 사람이 하려고 할 때 띄우는 창.
 * [로그인]은 이 창 안에서 바로 로그인해 화면을 떠나지 않고(방송 영상·입력 그대로) 하던 동작으로 돌아간다.
 * [회원가입]은 회원가입 화면으로 가고, 가입하면 지금 화면으로 돌아온다.
 */
export function LoginRequiredDialog({ open, onClose, onLoggedIn }: Props) {
  const [step, setStep] = useState<'ask' | 'login'>('ask');
  const navigate = useNavigate();
  const location = useLocation();
  const back = encodeURIComponent(location.pathname + location.search);

  const close = () => {
    setStep('ask');
    onClose();
  };

  return (
    <Dialog open={open} onClose={close} title="로그인이 필요합니다" className="w-[440px]">
      {step === 'ask' ? (
        <div className="flex flex-col gap-6">
          <p className="text-body text-neutral-700">로그인이 필요합니다. 로그인 하시겠습니까?</p>
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={close}>
              취소
            </Button>
            <Button variant="outline" onClick={() => navigate(`/signup?redirect=${back}`)}>
              회원가입
            </Button>
            <Button onClick={() => setStep('login')}>로그인</Button>
          </div>
        </div>
      ) : (
        <LoginForm
          back={back}
          onBack={() => setStep('ask')}
          onLoggedIn={() => {
            setStep('ask');
            onLoggedIn();
          }}
        />
      )}
    </Dialog>
  );
}

function LoginForm({ back, onBack, onLoggedIn }: { back: string; onBack: () => void; onLoggedIn: () => void }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string>();

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(undefined);
    try {
      await login({ email, password });
      onLoggedIn();
    } catch (e) {
      setError(errorMessage(e));
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} aria-label="로그인" className="flex flex-col gap-4">
      <Input label="이메일" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
      <Input label="비밀번호" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
      {error && <Notice tone="error">{error}</Notice>}
      <Button type="submit" size="lg" loading={submitting} disabled={!email || !password}>
        로그인
      </Button>
      <div className="flex items-center justify-between text-label text-neutral-500">
        <button type="button" onClick={onBack} className="hover:text-neutral-900">
          ← 뒤로
        </button>
        <span className="flex gap-3">
          <Link to="/find-email" className="hover:text-neutral-900">
            이메일 찾기
          </Link>
          <Link to="/password-reset" className="hover:text-neutral-900">
            비밀번호 재설정
          </Link>
          <Link to={`/signup?redirect=${back}`} className="font-semibold text-neutral-900">
            회원가입
          </Link>
        </span>
      </div>
    </form>
  );
}
