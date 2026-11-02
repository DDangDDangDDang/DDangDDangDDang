import type { Schema } from '@/api';

/** 내 정보(GET /users/me) 캐시 키. 내 정보 화면의 여러 부품이 수정 뒤 이 캐시를 바꾼다. */
export const myInfoQueryKey = ['users', 'me'] as const;

export type MyInfo = Schema<'MyInfoResponse'>;
