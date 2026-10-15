import { useCallback, useEffect, useState } from 'react';
import { errorText, LiveStreamCard, LiveStreamHome, liveStreamApi } from './api';
import type { Log } from './App';

type Props = { log: Log; onSelect: (liveStreamId: number) => void };

// 서버가 시청자 수를 15초마다 갱신하므로 같은 주기로 다시 불러온다.
const REFRESH_MS = 15_000;

export default function HomePanel({ log, onSelect }: Props) {
  const [home, setHome] = useState<LiveStreamHome | null>(null);
  const [loadedAt, setLoadedAt] = useState<string>('');

  const load = useCallback(async () => {
    try {
      setHome(await liveStreamApi.home(10));
      setLoadedAt(new Date().toLocaleTimeString('ko-KR', { hour12: false }));
    } catch (e) {
      log(`홈 방송 목록 조회 실패: ${errorText(e)}`, 'error');
    }
  }, [log]);

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(), REFRESH_MS);
    return () => window.clearInterval(timer);
  }, [load]);

  return (
    <section className="panel home">
      <div className="row between">
        <h2>홈 · 진행 중인 방송</h2>
        <span className="meta">
          {loadedAt && `${loadedAt} 기준 · 15초마다 갱신 `}
          <button type="button" onClick={() => void load()}>새로고침</button>
        </span>
      </div>
      <div className="home-sections">
        <HomeSection
          title="시청자 많은 방송"
          empty="진행 중인 방송이 없어요"
          cards={home?.mostViewed ?? []}
          metric={(card) => `시청자 ${card.viewerCount}명`}
          onSelect={onSelect}
        />
        <HomeSection
          title="시청자 급증 방송"
          empty="최근 5분 동안 시청자가 늘어난 방송이 없어요"
          cards={home?.surging ?? []}
          metric={(card) => `+${card.viewerIncrease}명 (지금 ${card.viewerCount}명)`}
          onSelect={onSelect}
        />
        <div className="home-section">
          <h3>입찰 많은 방송</h3>
          <p className="meta">경매 기능이 생기면 보여 줄 예정이에요</p>
        </div>
      </div>
    </section>
  );
}

type SectionProps = {
  title: string;
  empty: string;
  cards: LiveStreamCard[];
  metric: (card: LiveStreamCard) => string;
  onSelect: (liveStreamId: number) => void;
};

function HomeSection({ title, empty, cards, metric, onSelect }: SectionProps) {
  return (
    <div className="home-section">
      <h3>{title}</h3>
      {cards.length === 0 ? (
        <p className="meta">{empty}</p>
      ) : (
        <ol className="home-cards">
          {cards.map((card) => (
            <li key={card.liveStreamId}>
              <button type="button" className="home-card" onClick={() => onSelect(card.liveStreamId)}>
                <strong>{card.title}</strong>
                <span className="sub">
                  #{card.liveStreamId} · {card.sellerNickname ?? '(알 수 없음)'} · {metric(card)}
                </span>
              </button>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
