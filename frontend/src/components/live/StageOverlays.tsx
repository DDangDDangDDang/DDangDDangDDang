import type { ChatMessage, Schema } from '@/api';
import { AuctionEffects } from '@/components/auction/AuctionEffects';
import { FeaturedAuctionSlot } from '@/components/auction/FeaturedAuctionOverlay';
import { DonationEffects } from '@/components/donation/DonationEffects';
import { cn } from '@/lib/cn';
import { OVERLAY_AREA_CLASS, type OverlayLayout } from '@/lib/overlay-layout';
import { OverlayBoxView } from './OverlayBoxView';
import { OverlayItem } from './OverlayItem';

/**
 * 영상 위 UI 전부를 판매자가 정한 화면 배치대로 그린다. 시청 화면과 스튜디오 미리보기가 같이 써서 판매자가 시청자 화면을 그대로 본다.
 * - 판매자가 넣은 텍스트·이미지 박스: 맨 아래에 그려, 경매 카드·안내를 가리지 않는다.
 * - 경매 카드(최대 2장 묶음): 시청 화면이면 누르면 입찰 창을 연다(onOpenAuction).
 * - 선물·입찰·낙찰 안내: 채팅 메시지로 온 것을 차례로 보여 준다.
 * 판매자가 숨긴 UI(hidden)는 그리지 않는다. 배치 영역은 영상 전체다. (OVERLAY_AREA_CLASS) LIVE 배지와 버튼 줄은 그 위에 그려진다.
 */
export function StageOverlays({
  layout,
  featuredAuctionIds,
  auctions,
  onOpenAuction,
  messages,
  sound,
}: {
  layout: OverlayLayout;
  featuredAuctionIds?: number[];
  auctions: Schema<'AuctionResponse'>[];
  onOpenAuction?: (auctionId: number, ongoing: boolean) => void;
  messages: ChatMessage[];
  sound: boolean;
}) {
  return (
    <div className={cn(OVERLAY_AREA_CLASS, 'pointer-events-none')}>
      {layout.boxes.map((box) => (
        <OverlayItem key={box.id} position={box} width={box.type === 'IMAGE' ? box.width : undefined}>
          <OverlayBoxView box={box} />
        </OverlayItem>
      ))}
      {!layout.featured.hidden && (
        <OverlayItem position={layout.featured} className="pointer-events-auto">
          <FeaturedAuctionSlot featuredAuctionIds={featuredAuctionIds} auctions={auctions} onOpen={onOpenAuction} />
        </OverlayItem>
      )}
      <AuctionEffects
        messages={messages}
        sound={sound}
        bidPosition={layout.bid}
        soldPosition={layout.sold}
        hideBid={layout.bid.hidden}
        hideSold={layout.sold.hidden}
        className="absolute inset-0"
      />
      {!layout.donation.hidden && (
        <OverlayItem position={layout.donation}>
          <DonationEffects messages={messages} sound={sound} />
        </OverlayItem>
      )}
    </div>
  );
}
