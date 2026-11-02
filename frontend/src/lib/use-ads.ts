import { useQuery } from '@tanstack/react-query';
import { api, call } from '@/api';
import { AD_PLACEMENT_CODE, toAd, type AdPlacement } from './ads';

/** 화면 배너: 이 자리의 켜진 광고. 광고는 자주 바뀌지 않아 5분 동안 다시 부르지 않는다 */
export function useAds(placement: AdPlacement, enabled = true) {
  const code = AD_PLACEMENT_CODE[placement];
  return useQuery({
    queryKey: ['ads', code],
    queryFn: () => call(api.GET('/api/v1/ads', { params: { query: { placement: code } } })),
    select: (ads) => (ads ?? []).map(toAd),
    staleTime: 5 * 60_000,
    enabled,
  });
}
