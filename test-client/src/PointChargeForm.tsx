import { FormEvent, useEffect, useState } from 'react';
import PortOne from '@portone/browser-sdk/v2';
import { errorText, pointChargeApi, type PointChargeCompleted } from './api';
import type { Log } from './App';

type Props = {
    log: Log;
    /** 충전이 끝나면(성공) 잔액과 내역을 다시 불러온다. */
    onCharged: () => void;
};

// 포트원 콘솔 > 결제 연동 > 연동 정보의 상점 ID와 테스트 채널 키. test-client/.env.local에 적는다.
const STORE_ID = import.meta.env.VITE_PORTONE_STORE_ID as string | undefined;
const CHANNEL_KEY = import.meta.env.VITE_PORTONE_CHANNEL_KEY as string | undefined;

const PRESETS = [1_000, 10_000, 50_000];

/** 모바일 결제 후 되돌아온 주소인지 (?paymentId=point-charge-...) */
export function hasChargeRedirect(): boolean {
    return new URLSearchParams(window.location.search).get('paymentId')?.startsWith('point-charge-') ?? false;
}

// StrictMode에서 effect가 두 번 돌아도 같은 결제를 두 번 확정 요청하지 않게 한다. (서버도 멱등이지만 로그가 겹친다)
let redirectHandled = false;

export default function PointChargeForm({ log, onCharged }: Props) {
    const [amount, setAmount] = useState('10000');
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState<{ text: string; error?: boolean } | null>(null);

    async function complete(paymentId: string) {
        const res = await pointChargeApi.complete(paymentId);
        showResult(res);
        if (res.status === 'COMPLETE') onCharged();
    }

    function showResult(res: PointChargeCompleted) {
        if (res.status === 'COMPLETE') {
            setMessage({ text: `${res.amount.toLocaleString()}P 충전 완료 (잔액 ${res.balance?.toLocaleString()}P)` });
            log(`포인트 충전 완료: ${res.amount}P, paymentId=${res.paymentId}`);
        } else if (res.status === 'PENDING') {
            setMessage({ text: '결제 확인 중입니다. 잠시 후 새로고침해 주세요.' });
            log(`포인트 충전 대기: paymentId=${res.paymentId}`);
        } else {
            setMessage({ text: '결제가 완료되지 않아 충전에 실패했습니다.', error: true });
            log(`포인트 충전 실패: paymentId=${res.paymentId}`, 'error');
        }
    }

    // 모바일: 결제창이 페이지를 떠났다가 redirectUrl?paymentId=...&code=... 로 돌아온다.
    useEffect(() => {
        if (redirectHandled || !hasChargeRedirect()) return;
        redirectHandled = true;
        const params = new URLSearchParams(window.location.search);
        const paymentId = params.get('paymentId')!;
        const code = params.get('code');
        window.history.replaceState(null, '', window.location.pathname);

        if (code) {
            setMessage({ text: `결제가 취소되었거나 실패했습니다. (${params.get('message') ?? code})`, error: true });
            return;
        }
        setBusy(true);
        complete(paymentId)
            .catch((err) => {
                setMessage({ text: errorText(err), error: true });
                log(errorText(err), 'error');
            })
            .finally(() => setBusy(false));
        // 마운트될 때 한 번만 확인한다.
    }, []);

    async function onSubmit(e: FormEvent) {
        e.preventDefault();
        if (!STORE_ID || !CHANNEL_KEY) {
            setMessage({ text: '.env.local에 VITE_PORTONE_STORE_ID, VITE_PORTONE_CHANNEL_KEY를 설정해 주세요.', error: true });
            return;
        }
        setBusy(true);
        setMessage(null);
        try {
            // 1. 서버에 충전 요청 생성 → paymentId 발급
            const charge = await pointChargeApi.request(Number(amount));
            log(`충전 요청 생성: ${charge.amount}P, paymentId=${charge.paymentId}`);

            // 2. 포트원 결제창 (PC는 팝업/iframe으로 열리고 여기서 결과가 돌아온다)
            const payment = await PortOne.requestPayment({
                storeId: STORE_ID,
                channelKey: CHANNEL_KEY,
                paymentId: charge.paymentId,
                orderName: charge.orderName,
                totalAmount: charge.amount,
                currency: 'KRW',
                payMethod: 'CARD',
                redirectUrl: `${window.location.origin}${window.location.pathname}`,
            });

            // code가 있으면 사용자가 창을 닫았거나 결제가 실패한 것. 충전 요청은 PENDING으로 남는다.
            if (!payment || payment.code !== undefined) {
                setMessage({ text: `결제가 취소되었거나 실패했습니다. (${payment?.message ?? '응답 없음'})`, error: true });
                log(`결제창 종료: ${payment?.code ?? '-'} ${payment?.message ?? ''}`, 'error');
                return;
            }

            // 3. 서버에 결제 확정 요청 (서버가 포트원에 직접 조회해서 금액 검증 후 지급)
            await complete(charge.paymentId);
        } catch (err) {
            setMessage({ text: errorText(err), error: true });
            log(errorText(err), 'error');
        } finally {
            setBusy(false);
        }
    }

    const amountNumber = Number(amount);

    return (
        <form onSubmit={onSubmit} className="stack">
            <div className="row">
                <label className="grow">
                    충전 금액 (1P = 1원)
                    <input
                        value={amount}
                        onChange={(e) => setAmount(e.target.value.replace(/\D/g, ''))}
                        inputMode="numeric"
                        maxLength={9}
                        required
                    />
                </label>
                {PRESETS.map((preset) => (
                    <button key={preset} type="button" onClick={() => setAmount(String(preset))} disabled={busy}>
                        {preset.toLocaleString()}
                    </button>
                ))}
                <button type="submit" className="primary" disabled={busy || !(amountNumber > 0)}>
                    충전하기
                </button>
            </div>
            <span className="hint">포트원 테스트 채널이면 실제 돈이 빠져나가지 않습니다. (일부 PG는 테스트 결제도 밤에 자동 취소)</span>
            {message && (
                <p className={message.error ? 'error' : 'meta'} role={message.error ? 'alert' : undefined}>
                    {message.text}
                </p>
            )}
        </form>
    );
}