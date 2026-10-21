import { useCallback, useState } from 'react';
import { getSession } from '@/api';
import { LoginRequiredDialog } from './LoginRequiredDialog';

/**
 * 로그인이 필요한 동작을 감싼다. 로그인했으면 바로 하고, 아니면 "로그인이 필요합니다" 창을 띄운 뒤
 * 그 창에서 로그인하면 이어서 한다. (화면을 떠나지 않으므로 방송 시청·입력 내용이 그대로 남는다)
 *
 *   const { guard, loginDialog } = useLoginGuard();
 *   <Button onClick={() => guard(() => openBidForm())}>입찰하기</Button>
 *   {loginDialog}
 */
export function useLoginGuard() {
  const [pending, setPending] = useState<(() => void) | null>(null);

  const guard = useCallback((action: () => void) => {
    if (getSession()) action();
    else setPending(() => action);
  }, []);

  const loginDialog = (
    <LoginRequiredDialog
      open={pending !== null}
      onClose={() => setPending(null)}
      onLoggedIn={() => {
        const action = pending;
        setPending(null);
        // 창이 닫히고 화면이 로그인 상태로 다시 그려진 뒤에 이어서 한다. (예: 열린 채팅 입력칸에 포커스)
        if (action) setTimeout(action, 0);
      }}
    />
  );

  return { guard, loginDialog };
}
