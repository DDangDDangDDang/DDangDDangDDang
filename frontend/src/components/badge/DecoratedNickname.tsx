import { Star } from 'lucide-react';
import { cn } from '@/lib/cn';
import { NICKNAME_BADGE_LABEL, nicknameBadgeKindOf, type NicknameBadge } from '@/lib/nickname-badge';

const COLOR_CLASS: Partial<Record<NicknameBadge, string>> = {
  COLOR_GOLD: 'text-gold-300',
  COLOR_MINT: 'text-nickname-mint',
  COLOR_CORAL: 'text-nickname-coral',
  COLOR_PURPLE: 'text-nickname-purple',
};

/**
 * 닉네임 꾸미기를 적용한 닉네임. 장착한 것이 없으면 닉네임만 그린다.
 * - badge(채팅 스킨): 천사 날개는 양옆에 하얀 날개, 악마 날개는 양옆에 붉은 박쥐 날개, 천사 링은 위에 금빛 링, 별은 앞에 별 + 금빛 닉네임
 * - title(채팅 뱃지): 닉네임 앞에 붙는 작은 칭호
 * - color(닉네임 색상): 닉네임 글자색. 별 스킨의 금빛보다 우선한다
 * 장식은 화면 읽기에서 뱃지 이름으로 한 번만 읽힌다.
 */
export function DecoratedNickname({
  nickname,
  badge,
  title,
  color,
  className,
}: {
  nickname: string;
  badge?: NicknameBadge | null;
  title?: NicknameBadge | null;
  color?: NicknameBadge | null;
  className?: string;
}) {
  const colorClass = color ? COLOR_CLASS[color] : undefined;
  const nameClass = cn(className, colorClass);
  const name = <NicknameSkin nickname={nickname} badge={badge} className={nameClass} hasColor={Boolean(colorClass)} />;
  if (!title) return name;
  return (
    <span data-title={title} className="inline-flex items-center gap-1 align-bottom">
      <span className="rounded-sm border border-brand-200 bg-brand-50 px-1 text-caption leading-4 font-semibold text-brand-700">{NICKNAME_BADGE_LABEL[title]}</span>
      {name}
    </span>
  );
}

/** 상점 미리 보기: 상품 하나만 적용한 닉네임 */
export function NicknameBadgePreview({ nickname, badge }: { nickname: string; badge: NicknameBadge }) {
  const kind = nicknameBadgeKindOf(badge);
  return (
    <DecoratedNickname nickname={nickname} badge={kind === 'SKIN' ? badge : undefined} title={kind === 'TITLE' ? badge : undefined} color={kind === 'COLOR' ? badge : undefined} />
  );
}

function NicknameSkin({ nickname, badge, className, hasColor }: { nickname: string; badge?: NicknameBadge | null; className?: string; hasColor: boolean }) {
  if (!badge) return <span className={className}>{nickname}</span>;
  const label = <span className="sr-only">({NICKNAME_BADGE_LABEL[badge]} 스킨)</span>;

  if (badge === 'HALO') {
    return (
      <span data-badge={badge} className="relative inline-block pt-1.5 align-bottom">
        <svg viewBox="0 0 24 6" aria-hidden="true" className="absolute top-0 left-1/2 h-1.5 w-5 -translate-x-1/2 text-gold-300">
          <ellipse cx="12" cy="3" rx="10.5" ry="2" fill="none" stroke="currentColor" strokeWidth="1.6" />
        </svg>
        <span className={className}>{nickname}</span>
        {label}
      </span>
    );
  }

  if (badge === 'STAR') {
    return (
      <span data-badge={badge} className="inline-flex items-center gap-0.5 align-bottom">
        <Star className="size-3 fill-current text-gold-300" aria-hidden="true" />
        <span className={cn(className, !hasColor && 'text-gold-300')}>{nickname}</span>
        {label}
      </span>
    );
  }

  const Wing = badge === 'ANGEL_WINGS' ? AngelWing : DevilWing;
  const wingColor = badge === 'ANGEL_WINGS' ? 'text-neutral-900' : 'text-red-300';
  return (
    <span data-badge={badge} className="inline-flex items-center gap-0.5 align-bottom">
      <Wing className={cn('h-3 w-3.5 -scale-x-100', wingColor)} />
      <span className={className}>{nickname}</span>
      <Wing className={cn('h-3 w-3.5', wingColor)} />
      {label}
    </span>
  );
}

/** 오른쪽으로 펼친 깃털 날개 (왼쪽은 좌우를 뒤집어 쓴다) */
function AngelWing({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 16 12" aria-hidden="true" className={className}>
      <path fill="currentColor" d="M1 8Q4 1 15 1Q13.4 2.9 11 3.6Q13.6 3.8 14.2 4.7Q11.8 6.1 9 6.1Q11.2 6.7 11.4 7.8Q7 9.8 1 8Z" />
    </svg>
  );
}

/** 오른쪽으로 펼친 박쥐 날개 (왼쪽은 좌우를 뒤집어 쓴다) */
function DevilWing({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 16 12" aria-hidden="true" className={className}>
      <path fill="currentColor" d="M1 7Q6 1 15 1L13.6 5Q12.2 3.9 11 6Q9.6 4.5 8 7Q6.6 5.6 5 8.2Q3 6.6 1 7Z" />
    </svg>
  );
}
