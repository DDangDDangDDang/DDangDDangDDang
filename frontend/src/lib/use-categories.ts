import { useQuery } from '@tanstack/react-query';
import { api, call } from '@/api';

/** 카테고리 목록 (대분류 › 소분류). 자주 바뀌지 않으므로 5분 동안 다시 불러오지 않는다. */
export function useCategories() {
  return useQuery({
    queryKey: ['categories'],
    queryFn: async () => (await call(api.GET('/api/v1/categories'))) ?? [],
    staleTime: 5 * 60 * 1000,
  });
}
