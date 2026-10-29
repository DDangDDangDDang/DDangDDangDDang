import { useId, useRef, useState, type KeyboardEvent, type PointerEvent, type ReactNode, type RefObject } from 'react';
import type { Schema } from '@/api';
import { BidNotice, SoldNotice } from '@/components/auction/AuctionEffects';
import { FeaturedAuctionOverlay } from '@/components/auction/FeaturedAuctionOverlay';
import { DonationNotice } from '@/components/donation/DonationEffects';
import { OverlayBoxView } from '@/components/live/OverlayBoxView';
import { OverlayItem } from '@/components/live/OverlayItem';
import { cn } from '@/lib/cn';
import {
  dragPercent,
  keyboardMove,
  OVERLAY_AREA_CLASS,
  OVERLAY_KEYS,
  OVERLAY_LABELS,
  type OverlayKey,
  type OverlayLayout,
} from '@/lib/overlay-layout';
import type { OverlaySelection } from './use-overlay-layout-editing';

type Auction = Schema<'AuctionResponse'>;

/**
 * 스튜디오 미리보기 위에서 방송 화면 배치를 고친다. 경매 카드·선물·입찰·낙찰 안내 견본과 판매자가 넣은 텍스트·이미지 박스를
 * 끌거나, 누른 뒤 화살표 키로 옮긴다. 숨긴 안내는 그리지 않는다. 누른 것을 고르면(onSelect) 바깥 패널에서 글자·크기를 고친다.
 * 배치 영역(점선)을 벗어나지 않고, 가장자리·가운데에 가까우면 붙는다. 저장·숨기기·박스 추가는 바깥(OverlayLayoutBar)에서 한다.
 * 경매 카드는 띄운 경매가 있으면 그 카드로, 없으면 견본 2장(가장 클 때)으로 보여 준다.
 */
export function OverlayLayoutEditor({
  layout,
  onChange,
  featured,
  selected,
  onSelect,
}: {
  layout: OverlayLayout;
  onChange: (layout: OverlayLayout) => void;
  /** 지금 영상 위에 띄운 경매 (없으면 견본) */
  featured: Auction[];
  /** 고른 것 (기본 안내 키 또는 박스 id) */
  selected?: OverlaySelection;
  onSelect?: (selection: OverlaySelection) => void;
}) {
  const areaRef = useRef<HTMLDivElement>(null);
  const hintId = useId();
  // 마지막으로 잡은 것을 위에 그린다. (겹쳐 있을 때 다시 잡기 쉽게)
  const [top, setTop] = useState<OverlaySelection>();
  const samples = useSamples(featured);

  return (
    <div ref={areaRef} role="group" aria-label="화면 배치 편집" className={cn(OVERLAY_AREA_CLASS, 'rounded-lg outline-2 -outline-offset-2 outline-white/40 outline-dashed')}>
      <p id={hintId} className="sr-only">
        끌어서 옮기거나, 누른 뒤 화살표 키로 옮겨요. Shift와 함께 누르면 크게 움직여요.
      </p>
      {layout.boxes.map((box, index) => (
        <DraggableOverlay
          key={box.id}
          label={`${box.type === 'TEXT' ? '텍스트 박스' : '이미지 박스'} ${index + 1}`}
          hintId={hintId}
          position={box}
          width={box.type === 'IMAGE' ? box.width : undefined}
          areaRef={areaRef}
          raised={top === box.id}
          selected={selected === box.id}
          onGrab={() => {
            setTop(box.id);
            onSelect?.(box.id);
          }}
          onMove={(position) => onChange({ ...layout, boxes: layout.boxes.map((b) => (b.id === box.id ? { ...b, ...position } : b)) })}
        >
          <OverlayBoxView box={box} />
        </DraggableOverlay>
      ))}
      {OVERLAY_KEYS.filter((key) => !layout[key].hidden).map((key) => (
        <DraggableOverlay
          key={key}
          label={OVERLAY_LABELS[key]}
          hintId={hintId}
          position={layout[key]}
          areaRef={areaRef}
          raised={top === key}
          selected={selected === key}
          onGrab={() => {
            setTop(key);
            onSelect?.(key);
          }}
          onMove={(position) => onChange({ ...layout, [key]: { ...layout[key], ...position } })}
        >
          {samples[key]}
        </DraggableOverlay>
      ))}
    </div>
  );
}

/** 견본: 실제 안내와 같은 모양이라 시청자에게 보일 크기 그대로 자리를 잡을 수 있다. */
function useSamples(featured: Auction[]): Record<OverlayKey, ReactNode> {
  // 견본 경매의 마감 시각은 처음 그릴 때 한 번 정한다. (10분 남음)
  const [sampleEndAt] = useState(() => new Date(Date.now() + 10 * 60_000).toISOString().slice(0, 19));
  const cards: Auction[] =
    featured.length > 0
      ? featured
      : [1, 2].map((n) => ({ id: -n, productName: `경매 카드 견본 ${n}`, status: 'ONGOING', currentPrice: 12_000, bidCount: 3, endAt: sampleEndAt }));
  return {
    featured: (
      <div className="flex flex-col gap-2">
        {cards.map((auction) => (
          <FeaturedAuctionOverlay key={auction.id} auction={auction} />
        ))}
      </div>
    ),
    donation: <DonationNotice effect={{ id: 'sample-donation', nickname: '시청자', amount: 1_000, message: '방송 잘 보고 있어요!' }} />,
    bid: (
      <BidNotice
        effect={{ kind: 'BID', id: 'sample-bid', nickname: '시청자', productName: '상품 이름', amount: 13_000, previousAmount: 10_000, tier: 'up30' }}
        compact={false}
      />
    ),
    sold: <SoldNotice effect={{ kind: 'SOLD', id: 'sample-sold', nickname: '시청자', productName: '상품 이름', amount: 15_000 }} compact={false} />,
  };
}

type Point = { x: number; y: number };
type Drag = { pointerId: number; startX: number; startY: number; from: Point; freeX: number; freeY: number };

/**
 * 옮길 수 있는 견본 하나. 견본 위를 덮는 투명한 버튼이 끌기·키보드를 받는다. (견본 자체는 화면 읽기 프로그램에 숨긴다)
 * 끈 거리를 남는 공간(배치 영역 - 견본 크기)에 대한 비율로 바꿔 위치(%)를 정한다.
 */
function DraggableOverlay({
  label,
  hintId,
  position,
  width,
  areaRef,
  raised,
  selected = false,
  onGrab,
  onMove,
  children,
}: {
  label: string;
  hintId: string;
  position: Point;
  /** 이미지 박스 폭 (영상 폭의 %) */
  width?: number;
  areaRef: RefObject<HTMLDivElement | null>;
  raised: boolean;
  selected?: boolean;
  onGrab: () => void;
  onMove: (position: Point) => void;
  children: ReactNode;
}) {
  const itemRef = useRef<HTMLDivElement>(null);
  const dragRef = useRef<Drag | null>(null);

  const onPointerDown = (event: PointerEvent<HTMLButtonElement>) => {
    const area = areaRef.current;
    // 여백을 포함한 바깥 상자(OverlayItem) 크기로 남는 공간을 잰다. (위치 %는 바깥 상자 기준)
    const item = itemRef.current?.parentElement;
    if (!area || !item) return;
    event.currentTarget.setPointerCapture(event.pointerId);
    onGrab();
    dragRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      from: position,
      freeX: area.clientWidth - item.offsetWidth,
      freeY: area.clientHeight - item.offsetHeight,
    };
  };
  const onPointerMove = (event: PointerEvent<HTMLButtonElement>) => {
    const drag = dragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    onMove({ x: dragPercent(drag.from.x, event.clientX - drag.startX, drag.freeX), y: dragPercent(drag.from.y, event.clientY - drag.startY, drag.freeY) });
  };
  const endDrag = () => {
    dragRef.current = null;
  };
  const onKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    const next = keyboardMove(position, event.key, event.shiftKey);
    if (!next) return;
    event.preventDefault();
    onGrab();
    onMove(next);
  };

  return (
    <OverlayItem position={position} width={width} className={raised ? 'z-20' : 'z-10'}>
      <div ref={itemRef} className="relative">
        <div aria-hidden="true" className="pointer-events-none">
          {children}
        </div>
        <button
          type="button"
          aria-label={`${label} 위치 옮기기`}
          aria-describedby={hintId}
          aria-pressed={selected}
          data-overlay-handle={label}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={endDrag}
          onPointerCancel={endDrag}
          onKeyDown={onKeyDown}
          className={cn(
            'absolute inset-0 cursor-grab touch-none rounded-lg outline-2 outline-dashed hover:outline-gold-300 focus-visible:outline-gold-300 active:cursor-grabbing',
            selected ? 'outline-gold-300 outline-solid' : 'outline-white/80',
          )}
        >
          <span className="absolute top-1.5 left-1.5 rounded-sm bg-stage/90 px-1.5 py-0.5 text-caption font-semibold whitespace-nowrap text-white">{label}</span>
        </button>
      </div>
    </OverlayItem>
  );
}
