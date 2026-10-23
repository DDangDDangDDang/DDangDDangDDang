import { useEffect, useRef, useState } from 'react';
import type { ChatMessage } from '@/api';

export type DonationEffect = { id: string; nickname: string; amount: number; message?: string };

/** 선물 이펙트 하나를 보여 주는 시간 */
export const DONATION_EFFECT_MS = 4_000;

/**
 * 방송 채팅으로 온 도네이션 선물(type DONATION)을 하나씩 차례로 보여 준다. (방송 화면 이펙트)
 * - 여러 개가 한꺼번에 오면 줄을 세워 하나가 끝나면 다음 것을 보여 준다.
 * - 화면을 처음 그릴 때 이미 있던 메시지는 다시 보여 주지 않는다. (스튜디오로 돌아왔을 때 지난 선물이 다시 뜨지 않게)
 */
export function useDonationEffects(messages: ChatMessage[], durationMs = DONATION_EFFECT_MS): DonationEffect | undefined {
  const seenRef = useRef<Set<string> | null>(null);
  const [queue, setQueue] = useState<DonationEffect[]>([]);
  const [current, setCurrent] = useState<DonationEffect>();

  useEffect(() => {
    if (seenRef.current === null) {
      seenRef.current = new Set(messages.map((message) => message.messageId));
      return;
    }
    const seen = seenRef.current;
    const fresh: DonationEffect[] = [];
    for (const message of messages) {
      if (seen.has(message.messageId)) continue;
      seen.add(message.messageId);
      if (message.type === 'DONATION') {
        fresh.push({
          id: message.messageId,
          nickname: message.nickname || '알 수 없음',
          amount: message.amount ?? 0,
          message: message.content ?? undefined,
        });
      }
    }
    if (fresh.length > 0) setQueue((list) => [...list, ...fresh]);
  }, [messages]);

  // 지금 보여 주는 것이 없으면 줄의 맨 앞을 꺼낸다.
  useEffect(() => {
    if (current || queue.length === 0) return;
    setCurrent(queue[0]);
    setQueue((list) => list.slice(1));
  }, [current, queue]);

  useEffect(() => {
    if (!current) return;
    const timer = setTimeout(() => setCurrent(undefined), durationMs);
    return () => clearTimeout(timer);
  }, [current, durationMs]);

  return current;
}
