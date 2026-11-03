import type { Schema } from '@/api';

/*
 * 닉네임 꾸미기: 라이브 방송 채팅에서 닉네임을 꾸미는 장식. 마일리지 상점에서 사면 영구로 가지고, 종류마다 하나씩 장착한다.
 * 종류는 채팅 스킨(둘레 장식), 채팅 뱃지(앞에 붙는 칭호), 닉네임 색상 세 가지다.
 */

export type NicknameBadge = NonNullable<Schema<'MyBadgesResponse'>['owned']>[number];
export type NicknameBadgeKind = NonNullable<Schema<'ShopItemResponse'>['badgeKind']>;

export const NICKNAME_BADGE_LABEL: Record<NicknameBadge, string> = {
  ANGEL_WINGS: '천사 날개',
  DEVIL_WINGS: '악마 날개',
  HALO: '천사 링',
  STAR: '별',
  TITLE_BID_KING: '입찰왕',
  TITLE_WIN_MASTER: '낙찰 장인',
  TITLE_REGULAR: '단골손님',
  TITLE_BIG_SPENDER: '큰손',
  COLOR_GOLD: '금색',
  COLOR_MINT: '민트',
  COLOR_CORAL: '코랄',
  COLOR_PURPLE: '보라',
};

/** 화면에 보여 주는 순서 */
export const NICKNAME_BADGE_KINDS: { kind: NicknameBadgeKind; label: string }[] = [
  { kind: 'SKIN', label: '채팅 스킨' },
  { kind: 'TITLE', label: '채팅 뱃지' },
  { kind: 'COLOR', label: '닉네임 색상' },
];

export function nicknameBadgeKindOf(badge: NicknameBadge): NicknameBadgeKind {
  if (badge.startsWith('TITLE_')) return 'TITLE';
  if (badge.startsWith('COLOR_')) return 'COLOR';
  return 'SKIN';
}

/** 내 꾸미기 (상점·채팅 미리 보기가 같은 캐시를 쓴다) */
export const MY_BADGES_KEY = ['users', 'me', 'badges'] as const;
