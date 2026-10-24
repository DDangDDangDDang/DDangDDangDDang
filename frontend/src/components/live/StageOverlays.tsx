import type { ChatMessage, Schema } from '@/api';
import { AuctionEffects } from '@/components/auction/AuctionEffects';
import { FeaturedAuctionSlot } from '@/components/auction/FeaturedAuctionOverlay';
import { DonationEffects } from '@/components/donation/DonationEffects';
import { cn } from '@/lib/cn';
import { OVERLAY_AREA_CLASS, type OverlayLayout } from '@/lib/overlay-layout';
import { OverlayItem } from './OverlayItem';

/**
 * 영상 위 UI 전부를 판매자가 정한 화면 배치대로 그린다. 시청 화면과 스튜디오 미리보기가 같이 써서 판매자가 시청자 화면을 그대로 본다.
 * - 경매 카드(최대 2장 묶음): 시청 화면이면 누르면 입찰 창을 연다(onOpenAuction).
 * - 선물·입찰·낙찰 안내: 채팅 메시지로 온 것을 차례로 보여 준다.
 * 배치 영역은 LIVE 배지와 아래 버튼 줄을 피한다. (OVERLAY_AREA_CLASS)
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
      <OverlayItem position={layout.featured} className="pointer-events-auto">
        <FeaturedAuctionSlot featuredAuctionIds={featuredAuctionIds} auctions={auctions} onOpen={onOpenAuction} />
      </OverlayItem>
      <AuctionEffects messages={messages} sound={sound} bidPosition={layout.bid} soldPosition={layout.sold} className="absolute inset-0" />
      <OverlayItem position={layout.donation}>
        <DonationEffects messages={messages} sound={sound} />
      </OverlayItem>
    </div>
  );
}
