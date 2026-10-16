import { FormEvent, useCallback, useEffect, useState } from 'react';
import { errorText, LiveStreamCard, LiveStreamStatus, liveStreamApi, scheduleText } from './api';
import type { Log } from './App';

type Props = { log: Log; userId: number; onSelect: (liveStreamId: number) => void };

const TABS: [LiveStreamStatus, string][] = [
  ['LIVE', '방송 중'],
  ['SCHEDULED', '방송 예정'],
  ['ENDED', '종료'],
];

// 상태마다 카드에 보여 줄 시간·숫자가 다르다.
function detail(card: LiveStreamCard): string {
  if (card.status === 'LIVE') return `시청자 ${card.viewerCount}명`;
  if (card.status === 'SCHEDULED') return card.scheduledStartAt ? `${scheduleText(card.scheduledStartAt)} 시작 예정` : '시작 예정';
  return card.endedAt ? `${scheduleText(card.endedAt)} 종료` : '종료';
}

export default function StreamListPanel({ log, userId, onSelect }: Props) {
  const [status, setStatus] = useState<LiveStreamStatus>('LIVE');
  const [keywordInput, setKeywordInput] = useState('');
  // 검색 버튼을 눌렀을 때의 검색어. 입력 중에는 다시 불러오지 않는다.
  const [keyword, setKeyword] = useState('');
  const [mineOnly, setMineOnly] = useState(false);
  const [cards, setCards] = useState<LiveStreamCard[]>([]);
  const [cursor, setCursor] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(
    async (more: boolean, from: number | null) => {
      setLoading(true);
      try {
        const page = await liveStreamApi.list({
          status,
          keyword: keyword || undefined,
          sellerId: mineOnly ? userId : undefined,
          cursor: more ? from : null,
        });
        setCards((prev) => (more ? [...prev, ...page.content] : page.content));
        setCursor(page.nextCursor);
      } catch (e) {
        log(`방송 목록 조회 실패: ${errorText(e)}`, 'error');
      } finally {
        setLoading(false);
      }
    },
    [status, keyword, mineOnly, userId, log],
  );

  // 탭·검색어·내 방송만 조건이 바뀌면 처음부터 다시 불러온다.
  useEffect(() => {
    void load(false, null);
  }, [load]);

  function search(e: FormEvent) {
    e.preventDefault();
    setKeyword(keywordInput.trim());
  }

  return (
    <section className="panel stream-list">
      <div className="row between">
        <h2>방송 목록 · 검색</h2>
        <span className="row">
          {TABS.map(([value, label]) => (
            <button
              key={value}
              type="button"
              className={status === value ? 'primary' : undefined}
              aria-pressed={status === value}
              onClick={() => setStatus(value)}
            >
              {label}
            </button>
          ))}
        </span>
      </div>

      <form className="row" onSubmit={search}>
        <label className="grow">
          검색어 (제목·설명·판매자 닉네임)
          <input value={keywordInput} onChange={(e) => setKeywordInput(e.target.value)} maxLength={50} />
        </label>
        <label className="checkbox">
          <input type="checkbox" checked={mineOnly} onChange={(e) => setMineOnly(e.target.checked)} />
          내 방송만
        </label>
        <button type="submit" className="primary">검색</button>
        {keyword && (
          <button type="button" onClick={() => { setKeywordInput(''); setKeyword(''); }}>검색어 지우기</button>
        )}
      </form>

      {cards.length === 0 ? (
        <p className="meta">{loading ? '불러오는 중…' : keyword ? `"${keyword}" 검색 결과가 없어요` : '방송이 없어요'}</p>
      ) : (
        <ol className="home-cards">
          {cards.map((card) => (
            <li key={card.liveStreamId}>
              <button type="button" className="home-card" onClick={() => onSelect(card.liveStreamId)}>
                <strong>{card.title}</strong>
                <span className="sub">
                  #{card.liveStreamId} · {card.sellerNickname ?? '(알 수 없음)'} · {detail(card)}
                </span>
              </button>
            </li>
          ))}
        </ol>
      )}
      {cursor && (
        <button type="button" onClick={() => void load(true, cursor)} disabled={loading}>더 보기</button>
      )}
    </section>
  );
}
