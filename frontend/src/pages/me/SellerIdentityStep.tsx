import * as PortOne from '@portone/browser-sdk/v2';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { BadgeCheck, Smartphone } from 'lucide-react';
import { api, call, errorMessage, type Schema } from '@/api';
import { Button, Notice } from '@/components/ui';
import { formatBirthDate, identityConfig, MY_IDENTITY_KEY, newIdentityVerificationId } from '@/lib/identity-verification';

type Identity = Schema<'SellerIdentityResponse'>;


/** 본인인증 창을 닫았거나 인증이 실패한 경우 (서버로 보내지 않는다) */
class IdentityWindowStopped extends Error {}

const IDENTITY_WINDOW_FAILED =
  '본인인증 창을 열지 못했어요. 브라우저의 팝업 차단을 해제한 뒤 다시 시도해 주세요. 계속되면 잠시 후 다시 시도하거나 문의해 주세요.';

/**
 * 판매자 신청 1단계: 휴대폰 본인인증. 만 19세 이상, 한 사람은 한 계정에서만 판매자가 될 수 있다.
 * 포트원 본인인증 창에서 인증을 마치면 그 ID를 서버로 보내고, 서버가 결과(실명·생년월일)를 직접 확인한다.
 * 본인인증 채널 설정이 없는 로컬 개발 화면에서는 창 없이 서버의 가짜 본인인증(seller.identity.provider=fake)을 쓴다.
 */
export function SellerIdentityStep({ identity, error }: { identity?: Identity; error?: string }) {
  const queryClient = useQueryClient();
  const config = identityConfig();
  const devFallback = !config && import.meta.env.DEV;

  const verify = useMutation({
    mutationFn: async () => {
      const identityVerificationId = newIdentityVerificationId();
      if (config) {
        let response;
        try {
          response = await PortOne.requestIdentityVerification({
            storeId: config.storeId,
            channelKey: config.channelKey,
            identityVerificationId,
            // PC에서는 인증 창이 이 페이지 위에 뜨고 결과는 Promise로 받는다. (SDK가 리디렉션용 주소를 요구한다)
            redirectUrl: window.location.href,
          });
        } catch (cause) {
          // 창 자체를 열지 못한 경우. KG이니시스 인증 창은 PC에서 팝업이라 브라우저가 팝업을 막으면 여기로 온다.
          // SDK 로드 실패·설정 오류도 여기로 오므로 원래 예외는 콘솔에 남긴다.
          console.error('본인인증 창을 열지 못했어요.', cause);
          throw new IdentityWindowStopped(IDENTITY_WINDOW_FAILED);
        }
        // code가 있으면 창을 닫았거나 인증이 실패한 것
        if (response?.code !== undefined) throw new IdentityWindowStopped(response.message || '본인인증을 마치지 않았어요.');
      }
      return call(api.POST('/api/v1/seller-applications/identity', { body: { identityVerificationId } }));
    },
    onSuccess: (saved) => queryClient.setQueryData(MY_IDENTITY_KEY, saved),
  });

  if (identity?.realName) {
    return (
      <section className="flex items-center gap-3 rounded-md border border-neutral-200 p-4">
        <BadgeCheck className="size-5 shrink-0 text-info-600" aria-hidden="true" />
        <div className="flex flex-col gap-0.5">
          <span className="text-body font-semibold">본인인증을 마쳤어요</span>
          <span className="text-label text-neutral-500">
            {identity.realName} · <span className="num">{formatBirthDate(identity.birthDate)}</span>생
          </span>
        </div>
      </section>
    );
  }

  return (
    <section className="flex flex-col gap-3 rounded-md border border-neutral-200 p-4" aria-labelledby="seller-identity-title">
      <div className="flex flex-col gap-1">
        <h3 id="seller-identity-title" className="text-label font-semibold text-neutral-900">
          휴대폰 본인인증
        </h3>
        <p className="text-label text-neutral-500">
          판매자는 만 19세 이상만 될 수 있고, 한 사람은 한 계정에서만 판매할 수 있어요. 인증한 이름과 생년월일은 심사와 정산에만 써요.
        </p>
      </div>
      {config || devFallback ? (
        <Button type="button" variant="outline" className="self-start" loading={verify.isPending} onClick={() => verify.mutate()}>
          <Smartphone className="size-4" aria-hidden="true" />
          {devFallback ? '본인인증 (개발용 가짜 인증)' : '휴대폰 본인인증'}
        </Button>
      ) : (
        <Notice tone="error">본인인증 설정이 필요해요. 관리자에게 문의해 주세요.</Notice>
      )}
      {verify.isError && <Notice tone="error">{verify.error instanceof IdentityWindowStopped ? verify.error.message : errorMessage(verify.error)}</Notice>}
      {error && !verify.isError && (
        <p role="alert" className="text-label text-red-500">
          {error}
        </p>
      )}
    </section>
  );
}
