import { ChevronLeft, ChevronRight, Megaphone, Pause, Play } from 'lucide-react';
import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { Badge } from '@/components/ui';
import { AD_SIZES, AD_SLIDE_MS, type Ad, type AdPlacement } from '@/lib/ads';
import { cn } from '@/lib/cn';
import { useAds } from '@/lib/use-ads';

/** 넘어가는 효과 시간. 효과가 끝났다는 신호(transitionend)를 못 받아도 이 시간 뒤에는 정리한다 */
const SLIDE_TRANSITION_MS = 500;

/**
 * 배너 광고 자리. 자리마다 크기 비율이 정해져 있어 광고가 없어도 같은 자리를 차지한다.
 * 광고에는 "광고" 표시를 붙이고, 광고주 링크는 새 탭으로 연다. (rel="sponsored")
 * - 광고 없음: 자리와 권장 이미지 크기를 보여 준다
 * - 1개: 그대로 보여 준다
 * - 2개 이상: 6초마다 다음 광고가 오른쪽에서 들어온다. 좌우 화살표와 일시정지·재생 버튼이 있다
 *
 * ads를 넘기면(테스트·미리보기) 서버에서 받지 않고 그 광고를 쓴다.
 */
export function AdBanner({ placement, ads, className }: { placement: AdPlacement; ads?: Ad[]; className?: string }) {
  const query = useAds(placement, ads === undefined);
  const list = ads ?? query.data ?? [];
  const { width, height } = AD_SIZES[placement];
  const frame = cn('relative block w-full shrink-0 overflow-hidden rounded-lg', className);
  const style: CSSProperties = { aspectRatio: `${width} / ${height}` };

  if (ads === undefined && query.isPending) {
    return <div aria-hidden="true" style={style} className={cn(frame, 'bg-neutral-50')} />;
  }

  if (list.length === 0) {
    return (
      <div
        role="note"
        aria-label="광고 자리"
        style={style}
        className={cn(frame, 'flex flex-col items-center justify-center gap-1 border border-dashed border-neutral-200 bg-neutral-50 text-center text-neutral-500')}
      >
        <Megaphone className="size-5 text-neutral-400" aria-hidden="true" />
        <span className="text-label font-semibold">광고 자리</span>
        <span className="num text-caption">
          {width * 2} × {height * 2}
        </span>
      </div>
    );
  }

  const [first] = list;
  if (list.length === 1 && first) {
    return <AdLink ad={first} style={style} className={cn(frame, 'bg-neutral-50 shadow-sm')} />;
  }
  return <AdCarousel key={list.map((ad) => ad.imageUrl).join('|')} ads={list} style={style} className={cn(frame, 'bg-neutral-50 shadow-sm')} />;
}

function AdLink({ ad, style, className, hidden = false }: { ad: Ad; style?: CSSProperties; className?: string; hidden?: boolean }) {
  return (
    <a href={ad.linkUrl} target="_blank" rel="noopener noreferrer sponsored" style={style} className={cn('relative block', className)} tabIndex={hidden ? -1 : undefined}>
      <img src={ad.imageUrl} alt={ad.alt} className="size-full object-cover" />
      <Badge className="absolute top-2 right-2 bg-stage/60 font-semibold text-white">광고</Badge>
    </a>
  );
}

/**
 * 광고 2개 이상: 가로로 이어 붙인 광고 줄을 한 칸씩 왼쪽으로 밀어, 다음 광고가 오른쪽에서 들어오게 한다.
 * 마지막 다음에도 같은 방향으로 첫 광고가 들어오도록 맨 끝에 첫 광고를 한 번 더 붙여 두고,
 * 그 칸에 도착하면 효과 없이 맨 앞으로 되돌린다. (이전으로 갈 때는 반대로 끝에서 시작한다)
 */
function AdCarousel({ ads, style, className }: { ads: Ad[]; style: CSSProperties; className: string }) {
  const count = ads.length;
  const slides = [...ads, ...ads.slice(0, 1)];
  const [position, setPosition] = useState(0);
  const [animate, setAnimate] = useState(true);
  const [playing, setPlaying] = useState(true);
  const pendingPrev = useRef(false);
  const current = position % count;

  const next = () => {
    setAnimate(true);
    setPosition((p) => (p >= count ? p : p + 1));
  };
  const prev = () => {
    if (position === 0) {
      // 끝(첫 광고를 한 번 더 붙인 칸)으로 효과 없이 옮긴 뒤, 그다음 그림에서 마지막 광고로 미끄러진다.
      pendingPrev.current = true;
      setAnimate(false);
      setPosition(count);
      return;
    }
    setAnimate(true);
    setPosition((p) => p - 1);
  };
  const goTo = (index: number) => {
    setAnimate(true);
    setPosition(index);
  };

  // 이전 광고: 끝 칸으로 옮긴 게 그려진 다음에 효과를 켜고 한 칸 움직인다.
  useEffect(() => {
    if (!pendingPrev.current || animate || position !== count) return;
    let second = 0;
    const first = requestAnimationFrame(() => {
      second = requestAnimationFrame(() => {
        pendingPrev.current = false;
        setAnimate(true);
        setPosition(count - 1);
      });
    });
    return () => {
      cancelAnimationFrame(first);
      cancelAnimationFrame(second);
    };
  }, [animate, position, count]);

  // 끝 칸(첫 광고 복사본)에 도착하면 효과 없이 맨 앞으로. 효과 끝 신호를 못 받는 경우(다른 탭 등)도 시간으로 정리한다.
  useEffect(() => {
    if (position !== count || pendingPrev.current) return;
    const timer = setTimeout(() => {
      setAnimate(false);
      setPosition(0);
    }, SLIDE_TRANSITION_MS);
    return () => clearTimeout(timer);
  }, [position, count]);

  // 자동 넘김. 광고가 바뀔 때마다 다시 6초를 센다. (화살표로 넘겨도 바로 또 넘어가지 않게)
  useEffect(() => {
    if (!playing) return;
    const timer = setTimeout(() => {
      setAnimate(true);
      setPosition((p) => (p >= count ? p : p + 1));
    }, AD_SLIDE_MS);
    return () => clearTimeout(timer);
  }, [playing, position, count]);

  return (
    <section aria-roledescription="carousel" aria-label="광고" style={style} className={cn(className, 'group')}>
      <div
        className="flex h-full"
        style={{ transform: `translateX(-${position * 100}%)`, transition: animate ? `transform ${SLIDE_TRANSITION_MS}ms ease` : 'none' }}
      >
        {slides.map((ad, index) => {
          const hidden = index !== position;
          return (
            <div
              key={index}
              role="group"
              aria-roledescription="slide"
              aria-label={`${(index % count) + 1} / ${count}`}
              aria-hidden={hidden || index === count}
              className="h-full w-full shrink-0"
            >
              <AdLink ad={ad} className="size-full" hidden={hidden || index === count} />
            </div>
          );
        })}
      </div>

      <CarouselButton label="이전 광고" onClick={prev} className="top-1/2 left-2 -translate-y-1/2">
        <ChevronLeft className="size-4" aria-hidden="true" />
      </CarouselButton>
      <CarouselButton label="다음 광고" onClick={next} className="top-1/2 right-2 -translate-y-1/2">
        <ChevronRight className="size-4" aria-hidden="true" />
      </CarouselButton>

      <div className="absolute bottom-2 left-1/2 flex -translate-x-1/2 items-center gap-1.5 rounded-full bg-stage/50 px-2 py-1">
        {ads.map((_, index) => (
          <button
            key={index}
            type="button"
            aria-label={`${index + 1}번째 광고 보기`}
            aria-current={index === current ? 'true' : undefined}
            onClick={() => goTo(index)}
            className={cn('size-1.5 rounded-full transition-colors', index === current ? 'bg-white' : 'bg-white/45 hover:bg-white/75')}
          />
        ))}
        <button
          type="button"
          aria-label={playing ? '자동 넘김 멈추기' : '자동 넘김 다시 시작'}
          aria-pressed={!playing}
          onClick={() => setPlaying((value) => !value)}
          className="ml-1 inline-flex size-5 items-center justify-center rounded-full text-white hover:bg-white/20"
        >
          {playing ? <Pause className="size-3" aria-hidden="true" /> : <Play className="size-3" aria-hidden="true" />}
        </button>
      </div>
    </section>
  );
}

function CarouselButton({ label, onClick, className, children }: { label: string; onClick: () => void; className: string; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      className={cn(
        'absolute inline-flex size-7 items-center justify-center rounded-full bg-stage/50 text-white transition-colors hover:bg-stage/70',
        className,
      )}
    >
      {children}
    </button>
  );
}
