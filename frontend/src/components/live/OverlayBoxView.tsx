import { cn } from '@/lib/cn';
import type { OverlayBox, OverlayTextSize, OverlayTextStyle } from '@/lib/overlay-layout';

const TEXT_STYLE_CLASS: Record<OverlayTextStyle, string> = {
  // 바탕 없이 흰 글자. 밝은 영상 위에서도 읽히게 그림자를 둔다.
  PLAIN: 'text-white [text-shadow:0_1px_3px_var(--color-black)]',
  HIGHLIGHT: 'rounded-md bg-brand-500 px-3 py-1.5 text-on-brand',
  DARK: 'rounded-md bg-stage/75 px-3 py-1.5 text-white',
};

const TEXT_SIZE_CLASS: Record<OverlayTextSize, string> = {
  SMALL: 'text-body',
  MEDIUM: 'text-title-md',
  LARGE: 'text-title-lg',
};

/**
 * 판매자가 방송 화면에 넣은 박스의 내용. 시청 화면과 스튜디오(미리보기·편집)가 같이 쓴다.
 * - 텍스트 박스: 영상 폭까지 넓어지고(OverlayItem), 넘치면 다음 줄로
 * - 이미지 박스: 바깥(OverlayItem)이 정한 폭에 맞춰 비율을 지킨다
 */
export function OverlayBoxView({ box }: { box: OverlayBox }) {
  if (box.type === 'IMAGE') {
    return <img src={box.imageUrl} alt="" draggable={false} className="block h-auto w-full rounded-md" />;
  }
  return (
    <p className={cn('font-bold break-words whitespace-pre-wrap', TEXT_STYLE_CLASS[box.textStyle], TEXT_SIZE_CLASS[box.textSize])}>{box.text}</p>
  );
}
