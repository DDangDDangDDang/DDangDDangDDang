import { describe, expect, it } from 'vitest';
import { CUSTOM_THUMBNAIL_MAX_BYTES, customThumbnailProblem, thumbnailSize } from './live-thumbnail';

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

describe('직접 올리는 썸네일 검사', () => {
  it('2MB 이하 JPG·PNG·WEBP만 올린다', () => {
    expect(customThumbnailProblem({ type: 'image/jpeg', size: 1000 })).toBeUndefined();
    expect(customThumbnailProblem({ type: 'image/png', size: CUSTOM_THUMBNAIL_MAX_BYTES })).toBeUndefined();
    expect(customThumbnailProblem({ type: 'image/webp', size: 1000 })).toBeUndefined();
  });

  it('GIF 같은 다른 형식, 빈 파일, 2MB보다 큰 파일은 이유를 알려 준다', () => {
    expect(customThumbnailProblem({ type: 'image/gif', size: 1000 })).toBe('JPG, PNG, WEBP 이미지만 올릴 수 있어요.');
    expect(customThumbnailProblem({ type: 'image/png', size: 0 })).toBe('빈 파일은 올릴 수 없어요.');
    expect(customThumbnailProblem({ type: 'image/png', size: CUSTOM_THUMBNAIL_MAX_BYTES + 1 })).toBe('2MB 이하 이미지만 올릴 수 있어요.');
  });
});
