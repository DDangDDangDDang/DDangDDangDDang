import { api, call } from '@/api';

/** 저장된 배송지 목록·기본 배송지. 추가·수정·삭제 뒤에는 이 키 전체를 무효화한다 */
export const addressesQueryKey = ['addresses'] as const;

export const addressListQuery = {
  queryKey: [...addressesQueryKey, 'list'] as const,
  queryFn: () => call(api.GET('/api/v1/users/me/addresses')),
};

export const defaultAddressQuery = {
  queryKey: [...addressesQueryKey, 'default'] as const,
  // 저장된 배송지가 없으면 서버가 data: null을 준다
  queryFn: async () => (await call(api.GET('/api/v1/users/me/addresses/default'))) ?? null,
};
