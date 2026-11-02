import type { Schema } from '@/api';

/**
 * 배너 광고 자리와 광고 규칙.
 * 광고는 관리자가 등록하고(/admin/ads) 화면은 자리별 켜진 광고를 서버에서 받아 온다. (useAds)
 * 한 자리에 광고가 2개 이상이면 AD_SLIDE_MS마다 다음 광고가 오른쪽에서 들어온다. (AdBanner)
 */
export type AdPlacement = 'home-top' | 'sidebar';
export type AdPlacementCode = NonNullable<Schema<'AdResponse'>['placement']>;

/** 화면 자리 이름 → 서버 자리 이름 */
export const AD_PLACEMENT_CODE: Record<AdPlacement, AdPlacementCode> = {
  'home-top': 'HOME_TOP',
  sidebar: 'SIDEBAR',
};

export const AD_PLACEMENT_LABEL: Record<AdPlacementCode, string> = {
  HOME_TOP: '홈 상단 가로 배너',
  SIDEBAR: '왼쪽 메뉴 세로 배너',
};

export interface Ad {
  imageUrl: string;
  /** 누르면 새 탭으로 여는 광고주 주소 */
  linkUrl: string;
  /** 이미지 대신 읽어 줄 광고 문구 */
  alt: string;
}

/**
 * 자리별 화면 크기(px). 이미지는 선명하게 보이도록 이 크기의 2배로 받는다.
 *  - home-top: 홈 본문 폭(1024px) 맨 위 가로 배너
 *  - sidebar: 왼쪽 "구독 중인 판매자" 아래 세로 배너 (사이드바 폭 240px)
 */
export const AD_SIZES: Record<AdPlacement, { width: number; height: number }> = {
  'home-top': { width: 1024, height: 128 },
  sidebar: { width: 240, height: 320 },
};

/** 서버 자리 이름 → 권장 이미지 크기 (화면 크기의 2배) */
export function recommendedAdImageSize(code: AdPlacementCode): string {
  const placement = (Object.keys(AD_PLACEMENT_CODE) as AdPlacement[]).find((key) => AD_PLACEMENT_CODE[key] === code) ?? 'home-top';
  const { width, height } = AD_SIZES[placement];
  return `${width * 2} × ${height * 2}`;
}

/** 광고가 2개 이상일 때 다음 광고로 넘어가는 간격 */
export const AD_SLIDE_MS = 6_000;

/** 서버가 받는 광고 이미지 형식·크기 (AdService와 같다) */
export const AD_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'] as const;
export const AD_IMAGE_MAX_BYTES = 5 * 1024 * 1024;

/** 올릴 수 없는 파일이면 이유, 올릴 수 있으면 undefined */
export function adImageProblem(file: { type: string; size: number }): string | undefined {
  if (!(AD_IMAGE_TYPES as readonly string[]).includes(file.type)) return 'JPG, PNG, WEBP, GIF 이미지만 올릴 수 있어요.';
  if (file.size === 0) return '빈 파일은 올릴 수 없어요.';
  if (file.size > AD_IMAGE_MAX_BYTES) return '5MB 이하 이미지만 올릴 수 있어요.';
  return undefined;
}

/** 광고 링크는 http·https만 받는다 (서버도 같은 규칙으로 막는다) */
export function adLinkProblem(raw: string): string | undefined {
  const link = raw.trim();
  if (!link) return '광고를 눌렀을 때 열 주소를 입력해 주세요.';
  try {
    const url = new URL(link);
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return 'http:// 또는 https://로 시작하는 주소만 쓸 수 있어요.';
  } catch {
    return 'http:// 또는 https://로 시작하는 주소를 입력해 주세요.';
  }
  return undefined;
}

export function toAd(response: Schema<'AdResponse'>): Ad {
  return { imageUrl: response.imageUrl ?? '', linkUrl: response.linkUrl ?? '', alt: response.altText ?? '' };
}
