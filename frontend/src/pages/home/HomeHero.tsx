import { Zap } from 'lucide-react';

/**
 * 홈 맨 위 브랜드 배너: 서비스 소개 문구.
 * 크기는 2048×320 비율이라 폭에 따라 높이가 함께 줄어든다. (최소 폭 1280px에서도 글자가 넘치지 않는 높이)
 * 브라우저 글자 크기를 키워 내용이 그 높이를 넘으면 배너가 내용만큼 늘어난다. (overflow-hidden을 두면 aspect-ratio가 높이를 고정해 글자가 잘린다)
 */
export function HomeHero() {
  return (
    <section
      aria-label="땅땅땅 소개"
      className="flex aspect-[2048/320] flex-col justify-center gap-2 rounded-lg border border-brand-200 bg-brand-50 px-12 py-4"
    >
      <span className="flex items-center gap-1.5 self-start rounded-full bg-brand-100 px-3 py-1 text-label font-bold text-brand-700">
        <Zap className="size-3.5" aria-hidden="true" />
        실시간 라이브 경매
      </span>
      <h1 className="text-display font-bold tracking-tight">
        땅! 내리치는 순간, <span className="text-brand-500">번개처럼 낙찰.</span>
      </h1>
      <p className="text-body-lg text-neutral-700">라이브로 보고, 실시간으로 입찰하고, 망치 소리와 함께 내 것이 되는 경매.</p>
    </section>
  );
}
