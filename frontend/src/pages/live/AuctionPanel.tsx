import { Gavel } from 'lucide-react';
import { Card, CardBody, CardHeader, CardTitle, Notice } from '@/components/ui';

/**
 * 경매 패널 자리. 방송과 경매는 별개 도메인이고 방송 화면은 경매 정보를 보여주기만 한다.
 * 이 방송의 경매 목록·입찰 API가 아직 없어서 자리만 두고 안내한다. (입찰 API가 생기면 여기를 채운다)
 */
export function AuctionPanel() {
  return (
    <Card aria-labelledby="auction-title">
      <CardHeader>
        <CardTitle id="auction-title">진행 중인 경매</CardTitle>
      </CardHeader>
      <CardBody className="flex flex-col items-center gap-3 pt-2 text-center">
        <span className="flex size-12 items-center justify-center rounded-lg bg-neutral-50 text-neutral-400">
          <Gavel className="size-6" aria-hidden="true" />
        </span>
        <p className="text-body-lg font-semibold">입찰 기능을 준비하고 있어요</p>
        <Notice className="w-full text-left">이 방송의 경매 상품과 실시간 입찰은 곧 이곳에서 할 수 있어요. 지금은 방송 시청과 채팅만 할 수 있어요.</Notice>
      </CardBody>
    </Card>
  );
}
