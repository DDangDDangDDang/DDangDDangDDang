import { useEffect, useState } from 'react';

/**
 * 지금 시각. intervalMs마다 다시 그린다. null이면 처음 시각에 멈춘다. (경매 남은 시간처럼 매초 바뀌는 표시에 쓴다)
 */
export function useNow(intervalMs: number | null): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    if (intervalMs === null) return;
    setNow(new Date());
    const timer = setInterval(() => setNow(new Date()), intervalMs);
    return () => clearInterval(timer);
  }, [intervalMs]);
  return now;
}
