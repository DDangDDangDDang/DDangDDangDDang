import { FormEvent, useCallback, useEffect, useState } from 'react';
import {
    dateTimeText,
    errorText,
    pointApi,
    userApi,
    type MyInfo,
    type PointHistory,
    type PointHistoryType,
    type PointWallet,
} from './api';
import type { Log } from './App';
import PointChargeForm, { hasChargeRedirect } from './PointChargeForm';

type Props = { log: Log };

const HISTORY_TYPE_LABEL: Record<PointHistoryType, string> = {
    CHARGE: '충전',
    HOLD: '입찰 홀딩',
    RELEASE: '홀딩 해제',
    PURCHASE: '낙찰·구매',
    SETTLE: '판매 정산',
    EXCHANGE: '환전 신청',
    EXCHANGE_REFUND: '환전 환불',
};

const won = (n: number) => `${n.toLocaleString()}P`;

/** 내 정보 조회·닉네임 수정, 포인트 잔액·내역 조회 */
export default function MyPagePanel({ log }: Props) {
    // 모바일 결제는 결제 후 이 페이지로 되돌아오므로, 그때는 패널을 펼쳐 충전을 마무리한다.
    const [open, setOpen] = useState(hasChargeRedirect);
    const [me, setMe] = useState<MyInfo | null>(null);
    const [nickname, setNickname] = useState('');
    const [wallet, setWallet] = useState<PointWallet | null>(null);
    const [histories, setHistories] = useState<PointHistory[]>([]);
    const [typeFilter, setTypeFilter] = useState<PointHistoryType | ''>('');
    const [nextCursor, setNextCursor] = useState<number | null>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const fail = useCallback(
        (err: unknown) => {
            setError(errorText(err));
            log(errorText(err), 'error');
        },
        [log],
    );

    const loadHistories = useCallback(
        async (type: PointHistoryType | '', cursor: number | null) => {
            const page = await pointApi.histories(type || null, cursor);
            setHistories((prev) => (cursor ? [...prev, ...page.content] : page.content));
            setNextCursor(page.hasNext ? page.nextCursor : null);
        },
        [],
    );

    const refresh = useCallback(async () => {
        setBusy(true);
        setError(null);
        try {
            const [info, w] = await Promise.all([userApi.me(), pointApi.wallet()]);
            setMe(info);
            setNickname(info.nickname);
            setWallet(w);
            await loadHistories(typeFilter, null);
        } catch (err) {
            fail(err);
        } finally {
            setBusy(false);
        }
    }, [fail, loadHistories, typeFilter]);

    // 펼칠 때와 필터를 바꿀 때 새로 불러온다.
    useEffect(() => {
        if (open) void refresh();
    }, [open, refresh]);

    async function onUpdateNickname(e: FormEvent) {
        e.preventDefault();
        setBusy(true);
        setError(null);
        try {
            const updated = await userApi.updateNickname(nickname.trim());
            setMe(updated);
            setNickname(updated.nickname);
            log(`닉네임 변경: ${updated.nickname} (새 Access Token으로 교체)`);
        } catch (err) {
            fail(err);
        } finally {
            setBusy(false);
        }
    }

    async function onMore() {
        setBusy(true);
        try {
            await loadHistories(typeFilter, nextCursor);
        } catch (err) {
            fail(err);
        } finally {
            setBusy(false);
        }
    }

    return (
        <section className="panel mypage">
            <div className="row between">
                <h2>마이페이지</h2>
                <div className="row">
                    {open && (
                        <button type="button" onClick={() => void refresh()} disabled={busy}>
                            새로고침
                        </button>
                    )}
                    <button type="button" onClick={() => setOpen(!open)}>
                        {open ? '접기' : '펼치기'}
                    </button>
                </div>
            </div>

            {open && (
                <>
                    {error && <p className="error" role="alert">{error}</p>}

                    {me && (
                        <div className="stack">
                            <h3>내 정보</h3>
                            <p className="meta">
                                id {me.userId} · {me.role} · 가입 {dateTimeText(me.createdAt)}
                            </p>
                            <p>
                                {me.email} · {me.phoneNumber}
                            </p>
                            <form onSubmit={onUpdateNickname} className="row">
                                <label className="grow">
                                    닉네임 (2~10자)
                                    <input value={nickname} onChange={(e) => setNickname(e.target.value)} minLength={2} maxLength={10} required />
                                </label>
                                <button type="submit" disabled={busy || nickname.trim() === me.nickname}>
                                    닉네임 변경
                                </button>
                            </form>
                            <span className="hint">이메일·휴대폰 번호 변경은 아직 지원하지 않습니다.</span>
                        </div>
                    )}

                    {wallet && (
                        <div className="stack">
                            <h3>포인트</h3>
                            <p>
                                사용 가능 <strong>{won(wallet.balance)}</strong> · 입찰에 묶임 {won(wallet.heldBalance)} · 합계{' '}
                                {won(wallet.totalBalance)}
                            </p>
                            <PointChargeForm log={log} onCharged={() => void refresh()} />
                        </div>
                    )}

                    {wallet && (
                        <div className="stack">
                            <div className="row between">
                                <h3>포인트 내역</h3>
                                <select
                                    value={typeFilter}
                                    onChange={(e) => setTypeFilter(e.target.value as PointHistoryType | '')}
                                    aria-label="내역 종류"
                                >
                                    <option value="">전체</option>
                                    {Object.entries(HISTORY_TYPE_LABEL).map(([value, label]) => (
                                        <option key={value} value={value}>
                                            {label}
                                        </option>
                                    ))}
                                </select>
                            </div>
                            {histories.length === 0 ? (
                                <p className="meta">내역이 없습니다.</p>
                            ) : (
                                <ol className="stack">
                                    {histories.map((h) => (
                                        <li key={h.historyId}>
                                            <strong>{HISTORY_TYPE_LABEL[h.type]}</strong> {won(h.amount)}
                                            <span className="sub">
                        {' '}
                                                → 사용 가능 {won(h.balanceAfter)} / 묶임 {won(h.heldBalanceAfter)}
                                                {h.refType && ` · ${h.refType} #${h.refId}`} · {dateTimeText(h.createdAt)}
                      </span>
                                        </li>
                                    ))}
                                </ol>
                            )}
                            {nextCursor && (
                                <button type="button" onClick={onMore} disabled={busy}>
                                    더 보기
                                </button>
                            )}
                        </div>
                    )}
                </>
            )}
        </section>
    );
}