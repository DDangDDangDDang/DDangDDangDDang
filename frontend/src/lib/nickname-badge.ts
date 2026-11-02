import type { Schema } from '@/api';

/*
 * 닉네임 뱃지: 라이브 방송 채팅에서 닉네임을 꾸미는 장식. 마일리지 상점에서 사면 영구로 가지고, 한 번에 하나만 장착한다.
 */

export type NicknameBadge = NonNullable<Schema<'MyBadgesResponse'>['equipped']>;

export const NICKNAME_BADGE_LABEL: Record<NicknameBadge, string> = {
  ANGEL_WINGS: '천사 날개',
  DEVIL_WINGS: '악마 날개',
  HALO: '천사 링',
  STAR: '별',
};

/** 내 뱃지 (상점·채팅 미리 보기가 같은 캐시를 쓴다) */
export const MY_BADGES_KEY = ['users', 'me', 'badges'] as const;
