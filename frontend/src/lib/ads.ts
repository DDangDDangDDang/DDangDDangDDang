/**
 * 배너 광고 자리와 지금 걸린 광고.
 * 광고 관리 API가 생기면 ADS 표 대신 서버에서 받아 오고, 자리 이름(AdPlacement)은 그대로 쓴다.
 */
export type AdPlacement = 'home-top' | 'sidebar';

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

/** 지금 걸린 광고. 비어 있는 자리에는 광고 자리 안내가 보인다. */
export const ADS: Partial<Record<AdPlacement, Ad>> = {};
