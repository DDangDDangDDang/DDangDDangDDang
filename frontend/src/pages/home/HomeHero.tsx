import { Zap } from 'lucide-react';
import { Link } from 'react-router';
import { buttonVariants } from '@/components/ui';
import { cn } from '@/lib/cn';

/** 겹쳐 그리는 번개·금·충격파가 망치와 함께 같은 기준(그림 전체)으로 움직이게 한다 */
const VIEW_BOX = '[transform-box:view-box]';

/**
 * 홈 맨 위 브랜드 배너: 서비스 소개 문구 + 망치 애니메이션.
 * 망치(묠니르 모양)가 옆으로 휘둘러 바닥을 찍으면 닿은 자리에서 번개가 터진다. (리디자인 시안)
 * 움직임 줄이기 설정이면 망치가 바닥에 닿은 순간의 그림으로 멈춰 보여 준다.
 */
export function HomeHero() {
  return (
    <section aria-label="땅땅땅 소개" className="flex items-center overflow-hidden rounded-lg border border-brand-200 bg-brand-50">
      <div className="flex min-w-0 flex-1 flex-col gap-4 px-12 py-10">
        <span className="flex items-center gap-1.5 self-start rounded-full bg-brand-100 px-3 py-1 text-label font-bold text-brand-700">
          <Zap className="size-3.5" aria-hidden="true" />
          실시간 라이브 경매
        </span>
        <h1 className="text-display font-bold tracking-tight">
          땅! 내리치는 순간,
          <br />
          <span className="text-brand-500">번개처럼 낙찰.</span>
        </h1>
        <p className="max-w-[420px] text-body-lg text-neutral-700">
          라이브로 보고, 실시간으로 입찰하고, 망치 소리와 함께 내 것이 되는 경매.
        </p>
        <div className="mt-1 flex gap-2.5">
          <Link to="/lives" className={buttonVariants({ variant: 'primary' })}>
            라이브 경매 보러 가기
          </Link>
          <Link to="/auctions" className={buttonVariants({ variant: 'outline' })}>
            경매 둘러보기
          </Link>
        </div>
      </div>
      <div className="flex w-[380px] shrink-0 items-center justify-center py-6 pr-12 pl-4">
        <HammerStrike />
      </div>
    </section>
  );
}

function HammerStrike() {
  const bolt = 'animate-hammer-bolt opacity-0 motion-reduce:animate-none motion-reduce:opacity-100';
  const crack = 'animate-hammer-crack opacity-0 motion-reduce:animate-none motion-reduce:opacity-100 motion-reduce:[stroke-dashoffset:0]';
  return (
    <svg viewBox="0 0 600 420" aria-hidden="true" className="aspect-[600/420] w-full max-w-[300px]">
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
      <text
        x="40"
        y="120"
        className="animate-hammer-ding origin-center fill-brand-500 text-[50px] font-bold opacity-0 [transform-box:fill-box] motion-reduce:animate-none motion-reduce:opacity-100"
      >
        땅!
      </text>
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
