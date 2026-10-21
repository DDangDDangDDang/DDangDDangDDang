import { MessageCircle } from 'lucide-react';
import { useParams } from 'react-router';
import { EmptyState } from '@/components/ui';
import { Conversation } from './Conversation';
import { RoomList } from './RoomList';

/**
 * 1:1 채팅: 왼쪽 대화방 목록 + 오른쪽 대화. 주소의 상대 회원 번호(/messages/:userId)로 대화방을 연다. (없으면 서버가 만든다)
 * 아무 회원에게나 대화를 시작할 수 있고, 경매 상세·방송 화면의 [판매자에게 문의]로 들어온다.
 */
export function MessagesPage() {
  const raw = useParams().userId;
  const otherUserId = raw ? Number(raw) : undefined;
  const valid = otherUserId !== undefined && Number.isInteger(otherUserId) && otherUserId > 0;

  return (
    <div className="flex h-[calc(100vh-150px)] min-h-[560px] gap-4">
      <RoomList activeUserId={valid ? otherUserId : undefined} />
      <section aria-label="대화" className="flex min-w-0 flex-1 flex-col overflow-hidden rounded-lg border border-neutral-100 bg-neutral-0 shadow-sm">
        {valid ? (
          <Conversation key={otherUserId} otherUserId={otherUserId} />
        ) : raw ? (
          <EmptyState title="대화 상대를 찾을 수 없어요" description="주소가 바뀌었거나 없는 회원이에요." />
        ) : (
          <div className="m-auto flex flex-col items-center gap-2 text-center">
            <MessageCircle className="size-10 text-neutral-400" aria-hidden="true" />
            <p className="text-body-lg font-semibold">대화를 골라 주세요</p>
            <p className="text-body text-neutral-500">경매 상세나 방송 화면에서 [판매자에게 문의]를 누르면 새 대화를 시작할 수 있어요.</p>
          </div>
        )}
      </section>
    </div>
  );
}
