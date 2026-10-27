import { useEffect, useState } from 'react';

/**
 * 마감 시각(ms)까지 남은 초를 1초마다 갱신한다. 마감 시각이 없으면 0.
 * 인증번호 유효시간(5분), 재요청 대기(1분) 같은 타이머에 쓴다.
 */
export function useCountdown(deadline: number | null): number {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (deadline === null) return;
    setNow(Date.now());
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [deadline]);

  if (deadline === null) return 0;
  return Math.max(0, Math.ceil((deadline - now) / 1000));
}
