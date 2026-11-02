import { Zap } from 'lucide-react';
import { cn } from '@/lib/cn';

/** 겹쳐 그리는 번개·금·충격파가 망치와 함께 같은 기준(그림 전체)으로 움직이게 한다 */
const VIEW_BOX = '[transform-box:view-box]';

/** "땅" 글자 세 개: 왼쪽 아래에서 오른쪽 위로 조금씩 올라가며 놓는다. 마지막 글자에만 느낌표 */
const DINGS = [
  { x: 20, y: 130, text: '땅', animation: 'animate-hammer-ding-1' },
  { x: 100, y: 115, text: '땅', animation: 'animate-hammer-ding-2' },
  { x: 180, y: 100, text: '땅!', animation: 'animate-hammer-ding-3' },
];

/**
 * 홈 맨 위 브랜드 배너: 왼쪽에 서비스 소개 문구, 제목("번개처럼 낙찰.") 끝에서 20px 오른쪽에 망치 애니메이션.
 * 망치는 빠르게 세 번 내리치고, 닿을 때마다 "땅"이 하나씩 생겨 "땅땅땅"이 된다.
 * 크기는 2048×320 비율이라 폭에 따라 높이가 함께 줄어든다. (최소 폭 1280px에서도 글자가 넘치지 않는 높이)
 * 망치 그림은 배너 폭에 맞춰 커지고 줄어들어 배너 높이 안에 들어간다.
 * 브라우저 글자 크기를 키워 내용이 그 높이를 넘으면 배너가 내용만큼 늘어난다. (overflow-hidden을 두면 aspect-ratio가 높이를 고정해 글자가 잘린다)
 */
export function HomeHero() {
  return (
    <section
      aria-label="땅땅땅 소개"
      className="flex aspect-[2048/320] items-center gap-[20px] rounded-lg border border-brand-200 bg-brand-50 py-4 pr-10 pl-12"
    >
      {/* 글 묶음 폭은 제목 폭만큼. 설명(p)은 w-0 min-w-full로 묶음 폭을 넓히지 않아 망치 위치가 제목 끝을 따른다. */}
      <div className="flex min-w-0 flex-none flex-col gap-2">
        <span className="flex items-center gap-1.5 self-start rounded-full bg-brand-100 px-3 py-1 text-label font-bold text-brand-700">
          <Zap className="size-3.5" aria-hidden="true" />
          실시간 라이브 경매
        </span>
        <h1 className="text-display font-bold tracking-tight">
          땅! 내리치는 순간, <span className="text-brand-500">번개처럼 낙찰.</span>
        </h1>
        <p className="w-0 min-w-full text-body-lg text-neutral-700">라이브로 보고, 실시간으로 입찰하고, 망치 소리와 함께 내 것이 되는 경매.</p>
      </div>
      <HammerStrike />
    </section>
  );
}

function HammerStrike() {
  const bolt = 'animate-hammer-bolt opacity-0 motion-reduce:animate-none motion-reduce:opacity-100';
  const crack = 'animate-hammer-crack opacity-0 motion-reduce:animate-none motion-reduce:opacity-100 motion-reduce:[stroke-dashoffset:0]';
  return (
    // 폭을 배너 폭의 16%로 두면 높이(600:420)가 배너 안쪽 높이(2048:320 - 위아래 여백)보다 항상 작다.
    <svg viewBox="0 0 600 420" aria-hidden="true" className="aspect-[600/420] w-[16%] shrink-0">
      <ellipse
        cx="265"
        cy="382"
        rx="190"
        ry="20"
        fill="none"
        strokeWidth="3"
        className={cn('animate-hammer-wave origin-[265px_382px] stroke-brand-500 opacity-0 motion-reduce:hidden', VIEW_BOX)}
      />
      <line x1="20" y1="382" x2="580" y2="382" strokeWidth="2" className="stroke-brand-200" />
      <g fill="none" strokeWidth="3" strokeLinejoin="round" strokeDasharray="160" strokeDashoffset="160" className="stroke-brand-500">
        <path d="M240 382 L196 392 L160 386 L116 398 L66 392" className={crack} />
        <path d="M290 382 L338 394 L376 386 L420 398 L470 390" className={crack} />
      </g>
      <g fill="none" strokeLinejoin="round" strokeLinecap="round" className={bolt}>
        <polyline points="222,378 184,326 204,314 146,236 170,224 108,130" strokeWidth="8" className="stroke-brand-300" />
        <polyline points="222,378 184,326 204,314 146,236 170,224 108,130" strokeWidth="2.5" className="stroke-white" />
        <polyline points="184,326 150,318 132,288" strokeWidth="3" className="stroke-brand-500" />
      </g>
      <g fill="none" strokeLinejoin="round" strokeLinecap="round" className={cn(bolt, '[animation-delay:50ms]')}>
        <polyline points="218,380 164,366 176,354 104,346 116,334 36,334" strokeWidth="5" className="stroke-brand-500" />
        <polyline points="312,380 368,368 358,356 438,352 428,340 530,344" strokeWidth="5" className="stroke-brand-500" />
        <polyline points="368,368 404,374 420,366" strokeWidth="3" className="stroke-brand-300" />
      </g>
      {/* 망치가 닿을 때마다 "땅"이 하나씩 생겨 세 번째에 "땅땅땅"이 완성된다. (애니메이션은 index.css hammer-ding-1~3) */}
      {DINGS.map((ding) => (
        <text
          key={ding.animation}
          x={ding.x}
          y={ding.y}
          className={cn(
            'origin-center fill-brand-500 text-[64px] font-bold opacity-0 [transform-box:fill-box] motion-reduce:animate-none motion-reduce:opacity-100',
            ding.animation,
          )}
        >
          {ding.text}
        </text>
      ))}
      <g className={cn('animate-hammer-swing origin-[480px_307px] motion-reduce:animate-none', VIEW_BOX)}>
        {/* 손잡이: 가죽끈을 감은 나무 자루, 끝에 손목 고리 */}
        <rect x="315" y="294" width="172" height="26" rx="5" className="fill-hammer-wood" />
        <g strokeWidth="3" className="stroke-hammer-wood-dark">
          {[346, 364, 382, 400, 418, 436, 454].map((x) => (
            <line key={x} x1={x} y1="294" x2={x + 12} y2="320" />
          ))}
        </g>
        <rect x="485" y="286" width="18" height="42" rx="4" className="fill-hammer-metal" />
        <path d="M503 298 C 532 288, 548 300, 542 316 C 536 332, 516 326, 503 316" fill="none" strokeWidth="6" className="stroke-hammer-wood" />
        {/* 머리: 아래 끝면으로 바닥을 찍는다 */}
        <rect x="313" y="288" width="20" height="38" className="fill-hammer-metal" />
        <rect x="215" y="232" width="100" height="150" rx="10" className="fill-hammer-steel" />
        <rect x="215" y="232" width="100" height="18" rx="6" className="fill-hammer-steel-light" />
        <rect x="215" y="364" width="100" height="18" rx="6" className="fill-hammer-steel-dark" />
        <g fill="none" strokeWidth="3" strokeLinecap="round" className="stroke-hammer-etch">
          <path d="M232 262 q 14 10 33 0 q 19 -10 33 0" />
          <path d="M232 352 q 14 -10 33 0 q 19 10 33 0" />
          <circle cx="265" cy="307" r="18" />
          <path d="M265 289 v36 M247 307 h36" />
        </g>
      </g>
    </svg>
  );
}
