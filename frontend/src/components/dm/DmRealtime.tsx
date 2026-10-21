import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { connectRealtime, REALTIME, type DmEvent } from '@/api';
import { emitDmEvent } from '@/lib/dm-events';

/**
 * 로그인한 동안 1:1 채팅 실시간 메시지(/user/queue/dm)를 받는다. 앱 전체에 하나만 둔다. (AppLayout)
 * 받으면 읽지 않은 수·대화방 목록을 다시 불러오고, 열린 대화 화면에 이벤트를 넘긴다.
 */
export function DmRealtime({ userId }: { userId: number }) {
  const queryClient = useQueryClient();

  useEffect(() => {
    // 끊긴 동안 온 메시지는 다시 오지 않는다. 다시 연결되면 메시지·읽지 않은 수·목록을 새로 불러온다.
    // (대화방 쿼리 ['dm', 'room', id]는 POST라 다시 부르지 않는다)
    let wasDisconnected = false;
    const connection = connectRealtime((status) => {
      if (status === 'reconnecting') wasDisconnected = true;
      if (status === 'connected' && wasDisconnected) {
        wasDisconnected = false;
        for (const key of ['messages', 'unread', 'rooms']) void queryClient.invalidateQueries({ queryKey: ['dm', key] });
      }
    });
    connection.subscribe<DmEvent>(REALTIME.dm, (event) => {
      void queryClient.invalidateQueries({ queryKey: ['dm', 'unread'] });
      void queryClient.invalidateQueries({ queryKey: ['dm', 'rooms'] });
      emitDmEvent(event);
    });
    return () => connection.close();
    // 계정이 바뀌면 다시 연결한다.
  }, [queryClient, userId]);

  return null;
}
