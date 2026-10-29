import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';
import { overlayPositionStyle } from '@/lib/overlay-layout';

/**
 * 방송 화면 배치 영역(OVERLAY_AREA_CLASS, 영상 전체) 안에 UI 하나를 판매자가 정한 위치(x·y %)에 놓는다.
 * 여백(p-4)이 있어 끝에 붙여도 내용은 영상 가장자리에서 16px 떨어진다.
 * 폭은 내용만큼(w-max)이고 영역보다 넓어지지 않는다. 내용의 최대 폭은 각 UI가 정한다.
 * width(%)를 주면 영역 폭의 그만큼으로 고정한다. (이미지 박스)
 */
export function OverlayItem({
  position,
  width,
  className,
  children,
}: {
  position: { x: number; y: number };
  width?: number;
  className?: string;
  children: ReactNode;
}) {
  return (
    <div
      className={cn('absolute max-w-full p-4', width === undefined && 'w-max', className)}
      style={{ ...overlayPositionStyle(position), ...(width === undefined ? {} : { width: `${width}%` }) }}
    >
      {children}
    </div>
  );
}
