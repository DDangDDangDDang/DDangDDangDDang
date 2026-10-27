import { Package } from 'lucide-react';
import { Badge } from '@/components/ui';
import { cn } from '@/lib/cn';
import { ORDER_STATUS_LABEL, type OrderStatus } from '@/lib/labels';

const STATUS_BADGE: Record<OrderStatus, 'outline' | 'info' | 'neutral'> = {
  ESCROW: 'outline',
  SHIPPING: 'info',
  COMPLETED: 'neutral',
};

export function OrderStatusBadge({ status }: { status?: OrderStatus }) {
  if (!status) return null;
  return <Badge variant={STATUS_BADGE[status]}>{ORDER_STATUS_LABEL[status]}</Badge>;
}

/** 상품 이미지. 이미지가 없으면 상자 아이콘을 둔 자리를 보여준다. (깨진 이미지처럼 보이지 않게) */
export function ProductImage({ src, alt, className }: { src?: string; alt: string; className?: string }) {
  if (!src) {
    return (
      <div className={cn('flex shrink-0 items-center justify-center rounded-md bg-neutral-50 text-neutral-400', className)}>
        <Package className="size-1/5 max-h-12 min-h-5 max-w-12 min-w-5" strokeWidth={1.5} aria-hidden="true" />
        <span className="sr-only">{alt} 이미지 없음</span>
      </div>
    );
  }
  return <img src={src} alt={alt} className={cn('shrink-0 rounded-md bg-neutral-50 object-cover', className)} />;
}
