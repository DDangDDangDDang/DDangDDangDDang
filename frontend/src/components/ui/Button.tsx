import type { VariantProps } from 'class-variance-authority';
import { Loader2 } from 'lucide-react';
import type { ButtonHTMLAttributes } from 'react';
import { cn } from '@/lib/cn';
import { buttonVariants } from './button-variants';

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> &
  VariantProps<typeof buttonVariants> & {
    /** 요청 중 표시. 누르지 못하게 막고 스피너를 보여준다. */
    loading?: boolean;
  };

export function Button({ className, variant, size, loading = false, disabled, children, type = 'button', ...props }: ButtonProps) {
  return (
    <button
      type={type}
      className={cn(buttonVariants({ variant, size }), className)}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
      {children}
    </button>
  );
}
