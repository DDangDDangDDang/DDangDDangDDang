import { describe, expect, it } from 'vitest';
import { thumbnailSize } from './live-thumbnail';

describe('썸네일 크기', () => {
  it('폭 640을 넘으면 비율을 지켜 줄이고, 작은 영상은 그대로 둔다', () => {
    expect(thumbnailSize(1280, 720)).toEqual({ width: 640, height: 360 });
    expect(thumbnailSize(1080, 1920)).toEqual({ width: 640, height: 1138 });
    expect(thumbnailSize(320, 240)).toEqual({ width: 320, height: 240 });
  });

  it('영상 크기를 아직 모르면 캡처하지 않는다', () => {
    expect(thumbnailSize(0, 0)).toBeUndefined();
  });
});
