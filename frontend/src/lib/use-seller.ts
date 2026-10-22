import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, call, type Schema } from '@/api';

type Profile = Schema<'SellerProfileResponse'>;

export const sellerProfileKey = (sellerId: number) => ['seller-profile', sellerId] as const;
/** 내가 구독한 판매자 (홈 왼쪽 목록) */
export const MY_SUBSCRIPTIONS_KEY = ['subscriptions', 'mine'] as const;

/** 판매자 프로필 (인증 여부·구독자 수·방송 기록·신뢰 지표). 시청 화면·경매 상세·프로필 창이 같은 캐시를 쓴다. */
export function useSellerProfile(sellerId: number | undefined) {
  return useQuery({
    queryKey: sellerProfileKey(sellerId ?? 0),
    queryFn: () => call(api.GET('/api/v1/sellers/{sellerId}/profile', { params: { path: { sellerId: sellerId ?? 0 } } })),
    enabled: sellerId !== undefined,
  });
}

/**
 * 구독·구독 취소. 성공하면 받은 구독자 수로 프로필을 바로 바꾸고, 구독 목록을 다시 불러온다.
 * 로그인 확인은 부르는 쪽에서 한다. (useLoginGuard)
 */
export function useSubscription(sellerId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (subscribe: boolean) => {
      const params = { params: { path: { sellerId } } };
      return subscribe
        ? call(api.PUT('/api/v1/sellers/{sellerId}/subscription', params))
        : call(api.DELETE('/api/v1/sellers/{sellerId}/subscription', params));
    },
    onSuccess: (result) => {
      if (result) {
        queryClient.setQueryData<Profile | undefined>(sellerProfileKey(sellerId), (profile) =>
          profile ? { ...profile, subscribed: result.subscribed, subscriberCount: result.subscriberCount } : profile,
        );
      }
      void queryClient.invalidateQueries({ queryKey: MY_SUBSCRIPTIONS_KEY });
    },
  });
}
